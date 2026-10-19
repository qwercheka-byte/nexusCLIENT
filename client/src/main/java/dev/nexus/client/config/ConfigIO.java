package dev.nexus.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.nexus.client.NexusClient;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * File access for configuration files.
 * <p>
 * All asynchronous operations run <b>sequentially</b> on Minecraft's shared IO worker executor
 * (no extra threads are created): each task is chained after the previous one, so a "save current
 * profile, then read the next one" sequence always happens in order.
 */
public final class ConfigIO {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

    /** Queues a task on the IO lane. */
    public synchronized <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> next = tail.handle((r, e) -> null).thenApplyAsync(ignored -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Util.getIoWorkerExecutor());
        tail = next;
        return next;
    }

    /** Blocks until all queued IO finished (used on shutdown only). */
    public void drain(long timeoutMs) {
        CompletableFuture<?> current;
        synchronized (this) {
            current = tail;
        }
        try {
            current.handle((r, e) -> null).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            NexusClient.LOGGER.warn("Timed out waiting for pending config writes", e);
        }
    }

    // ------------------------------------------------------------------ blocking helpers (IO thread / startup)

    /** Writes JSON atomically (temp file + move) so a crash never leaves a half-written file. */
    public static void writeJson(Path file, JsonElement json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Result of reading a JSON object file. */
    public record ReadResult(JsonObject json, boolean missing, boolean corrupt, Path backup) {}

    /**
     * Reads a JSON object. A corrupt file is moved into {@code backupDir} and reported, never thrown.
     */
    public static ReadResult readJson(Path file, Path backupDir) {
        if (!Files.isRegularFile(file)) return new ReadResult(null, true, false, null);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw new JsonParseException("Root is not an object");
            return new ReadResult(el.getAsJsonObject(), false, false, null);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            NexusClient.LOGGER.error("Config file {} is corrupt: {}", file, e.getMessage());
            Path backup = backup(file, backupDir, "corrupt");
            if (backup != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // file stays; it will be overwritten by the next save
                }
            }
            return new ReadResult(null, false, true, backup);
        }
    }

    /** Copies a file into the backup directory with a timestamp. */
    public static Path backup(Path file, Path backupDir, String reason) {
        try {
            Files.createDirectories(backupDir);
            String name = file.getFileName().toString();
            Path target = backupDir.resolve(name + "." + reason + "-" + LocalDateTime.now().format(STAMP) + ".bak");
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            NexusClient.LOGGER.error("Could not back up {}", file, e);
            return null;
        }
    }
}
