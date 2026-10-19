package dev.nexus.launcher.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Shared Gson instance and safe (atomic) file helpers. */
public final class Json {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Json() {}

    public static <T> T parse(String text, Class<T> type) {
        return GSON.fromJson(text, type);
    }

    public static <T> T parse(String text, Type type) {
        return GSON.fromJson(text, type);
    }

    public static JsonObject parseObject(String text) {
        JsonElement e = JsonParser.parseString(text);
        if (!e.isJsonObject()) throw new JsonParseException("Expected a JSON object");
        return e.getAsJsonObject();
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, type);
        } catch (JsonParseException e) {
            throw new IOException("Malformed JSON in " + file + ": " + e.getMessage(), e);
        }
    }

    public static JsonObject readObject(Path file) throws IOException {
        try {
            return parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Malformed JSON in " + file + ": " + e.getMessage(), e);
        }
    }

    public static void write(Path file, Object value) throws IOException {
        writeString(file, GSON.toJson(value));
    }

    /** Writes through a temporary file and an atomic rename, so a crash never leaves a half-written file. */
    public static void writeString(Path file, String text) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }
}
