package dev.nexus.launcher.net;

import dev.nexus.launcher.core.FileUtil;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Tasks;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Parallel downloader with size/SHA-1 verification and per-file retries. */
public final class Downloader {
    /**
     * @param sha1       expected SHA-1 or null
     * @param size       expected size or -1
     * @param verifyHash when false an existing file with the right size is trusted (used for content-addressed
     *                   assets, whose file name already is their hash)
     */
    public record Task(String url, Path target, String sha1, long size, boolean verifyHash, boolean executable) {
        public static Task of(String url, Path target, String sha1, long size) {
            return new Task(url, target, sha1, size, true, false);
        }
    }

    private Downloader() {}

    /** Returns true when the file must be (re)downloaded. */
    public static boolean needsDownload(Task t) throws IOException {
        if (!Files.isRegularFile(t.target())) return true;
        if (t.size() > 0 && Files.size(t.target()) != t.size()) return true;
        if (t.verifyHash() && t.sha1() != null && !t.sha1().isBlank()) {
            return !FileUtil.sha1(t.target()).equalsIgnoreCase(t.sha1());
        }
        return false;
    }

    public static void run(List<Task> tasks, int threads, Progress progress) throws IOException {
        Map<Path, Task> unique = new LinkedHashMap<>();
        for (Task t : tasks) unique.putIfAbsent(t.target().toAbsolutePath().normalize(), t);

        List<Task> needed = new ArrayList<>();
        for (Task t : unique.values()) {
            progress.checkCancelled();
            if (needsDownload(t)) needed.add(t);
        }
        if (needed.isEmpty()) return;

        long totalBytes = 0;
        for (Task t : needed) totalBytes += Math.max(0, t.size());
        progress.total(totalBytes);
        progress.set(0);
        Log.info("Downloading " + needed.size() + " file(s), " + FileUtil.humanBytes(totalBytes));

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(threads, needed.size())), Tasks.factory("nexus-dl"));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Task t : needed) futures.add(pool.submit(() -> {
                fetch(t, progress);
                return null;
            }));
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    pool.shutdownNow();
                    Throwable c = e.getCause();
                    if (c instanceof IOException io) throw io;
                    throw new IOException(c);
                } catch (InterruptedException e) {
                    pool.shutdownNow();
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Cancelled");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void fetch(Task t, Progress progress) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            progress.checkCancelled();
            long[] counted = {0};
            try {
                Http.download(t.url(), t.target(), n -> {
                    counted[0] += n;
                    progress.add(n);
                });
                if (t.sha1() != null && !t.sha1().isBlank()) {
                    String actual = FileUtil.sha1(t.target());
                    if (!actual.equalsIgnoreCase(t.sha1())) {
                        Files.deleteIfExists(t.target());
                        throw new IOException(String.format(Locale.ROOT, "Checksum mismatch for %s (expected %s, got %s)", t.url(), t.sha1(), actual));
                    }
                }
                if (t.executable()) FileUtil.makeExecutable(t.target());
                if (t.size() > 0 && counted[0] != t.size()) progress.add(t.size() - counted[0]);
                return;
            } catch (InterruptedIOException e) {
                throw e;
            } catch (HttpStatusException e) {
                progress.add(-counted[0]);
                if (e.status() == 404 || e.status() == 403) throw e;
                last = e;
            } catch (IOException e) {
                progress.add(-counted[0]);
                last = e;
            }
        }
        throw last;
    }
}
