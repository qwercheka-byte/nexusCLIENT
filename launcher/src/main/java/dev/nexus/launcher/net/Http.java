package dev.nexus.launcher.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.LongConsumer;

/** Thin wrapper over {@link HttpClient} with retries for transient failures. */
public final class Http {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final int ATTEMPTS = 3;

    private Http() {}

    public static String get(String url) throws IOException {
        return get(url, Map.of());
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = request(url).GET();
        headers.forEach(b::header);
        return sendForString(b.build(), true);
    }

    public static JsonElement getJson(String url) throws IOException {
        return parse(get(url), url);
    }

    public static JsonElement getJson(String url, Map<String, String> headers) throws IOException {
        return parse(get(url, headers), url);
    }

    public static JsonObject postJson(String url, JsonElement body) throws IOException {
        HttpRequest req = request(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        JsonElement e = parse(sendForString(req, false), url);
        if (!e.isJsonObject()) throw new IOException("Unexpected response from " + url);
        return e.getAsJsonObject();
    }

    /** POSTs a form. Non-2xx responses are returned (not thrown) because OAuth reports errors in the body. */
    public static Response postForm(String url, Map<String, String> form) throws IOException {
        StringJoiner sj = new StringJoiner("&");
        form.forEach((k, v) -> sj.add(enc(k) + "=" + enc(v)));
        HttpRequest req = request(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sj.toString()))
                .build();
        HttpResponse<String> r = send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), false);
        return new Response(r.statusCode(), r.body());
    }

    public record Response(int status, String body) {
        public JsonObject json() throws IOException {
            JsonElement e = parse(body, "response");
            if (!e.isJsonObject()) throw new IOException("Unexpected response: " + body);
            return e.getAsJsonObject();
        }
    }

    /**
     * Streams a URL into {@code target} through a temporary file. {@code onBytes} receives the number of bytes
     * read since the previous call (may be null).
     */
    public static void download(String url, Path target, LongConsumer onBytes) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(target.getFileName() + ".part" + Thread.currentThread().threadId());
        try {
            HttpResponse<InputStream> r = send(request(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream(), false);
            try (InputStream in = r.body()) {
                if (r.statusCode() / 100 != 2) {
                    throw new HttpStatusException(r.statusCode(), url, new String(in.readNBytes(2048), StandardCharsets.UTF_8));
                }
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Download cancelled");
                        out.write(buf, 0, n);
                        if (onBytes != null) onBytes.accept(n);
                    }
                }
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", BuildInfo.userAgent());
    }

    private static String sendForString(HttpRequest req, boolean retry) throws IOException {
        HttpResponse<String> r = send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), retry);
        if (r.statusCode() / 100 != 2) throw new HttpStatusException(r.statusCode(), req.uri().toString(), r.body());
        return r.body();
    }

    /** Sends a request; GETs are retried on network errors and 5xx/429 responses. */
    private static <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler, boolean retryStatus) throws IOException {
        boolean idempotent = req.method().equals("GET");
        IOException last = null;
        for (int attempt = 1; attempt <= (idempotent ? ATTEMPTS : 1); attempt++) {
            try {
                HttpResponse<T> r = CLIENT.send(req, handler);
                int s = r.statusCode();
                if (retryStatus && idempotent && (s >= 500 || s == 429) && attempt < ATTEMPTS) {
                    sleep(attempt);
                    continue;
                }
                return r;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Request interrupted");
            } catch (IOException e) {
                last = e;
                if (attempt < ATTEMPTS && idempotent) sleep(attempt);
            }
        }
        throw last != null ? last : new IOException("Request failed: " + req.uri());
    }

    private static void sleep(int attempt) throws InterruptedIOException {
        try {
            Thread.sleep(400L * attempt * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted");
        }
    }

    private static JsonElement parse(String body, String url) throws IOException {
        try {
            return com.google.gson.JsonParser.parseString(body);
        } catch (RuntimeException e) {
            throw new IOException("Invalid JSON from " + url, e);
        }
    }

    /** Convenience for {@link Json#str}. */
    public static String str(JsonObject o, String key) {
        return Json.str(o, key, null);
    }
}
