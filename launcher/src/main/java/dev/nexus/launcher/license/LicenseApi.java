package dev.nexus.launcher.license;

import com.google.gson.JsonObject;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.net.Http;

import java.io.IOException;

/**
 * Client of the nexus-licenses Cloudflare Worker (register / activate / auth / verify).
 * Real credentials never leave this machine: the server only ever sees the {@link Verifier} digest.
 * Error bodies keep the worker's machine code in {@link LicenseApiException#code} so the UI can
 * map them to human messages.
 */
public final class LicenseApi {
    /** Production worker. {@link Settings#licenseUrl} overrides this (e.g. after a domain migration). */
    public static final String BUILT_IN_URL = "https://nexus-licenses.qwercheka.workers.dev";

    private LicenseApi() {}

    /** Thrown when the worker answered with ok:false; {@link #code} is the worker's error code. */
    public static final class LicenseApiException extends IOException {
        public final String code;
        public final Long paidUntil; // present on EXPIRED when known

        LicenseApiException(String code, Long paidUntil) {
            super(code);
            this.code = code;
            this.paidUntil = paidUntil;
        }
    }

    /** Result of a successful auth/verify call. */
    public record Grant(String token, long paidUntil, boolean frozen) {}

    private static String base() {
        String u = Settings.get().licenseUrl;
        String b = (u == null || u.isBlank()) ? BUILT_IN_URL : u.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    /** Register a new account. Throws {@link LicenseApiException} LOGIN_TAKEN on duplicates. */
    public static void register(String login, String verifier) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("login", login);
        body.addProperty("verifier", verifier);
        requireOk(post("/register", body));
    }

    /** Spend an activation key; {@code days} are appended to the end of the current (or fresh) subscription. */
    public static long activate(String login, String verifier, String code) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("login", login);
        body.addProperty("verifier", verifier);
        body.addProperty("code", code.trim().toUpperCase());
        JsonObject r = requireOk(post("/activate", body));
        return r.has("paidUntil") ? r.get("paidUntil").getAsLong() : 0L;
    }

    /** Log in; on the first login this also binds the account to {@code hwid}. Returns a 14-day token. */
    public static Grant auth(String login, String verifier, String hwid) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("login", login);
        body.addProperty("verifier", verifier);
        body.addProperty("hwid", hwid);
        JsonObject r = requireOk(post("/auth", body));
        long paidUntil = r.has("paidUntil") ? r.get("paidUntil").getAsLong() : 0L;
        boolean frozen = r.has("frozen") && r.get("frozen").getAsBoolean();
        String token = r.has("token") ? r.get("token").getAsString() : "";
        return new Grant(token, paidUntil, frozen);
    }

    /** Re-verify a cached token (every few days while playing). */
    public static Grant verify(String token, String hwid) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        body.addProperty("hwid", hwid);
        JsonObject r = requireOk(post("/verify", body));
        long paidUntil = r.has("paidUntil") ? r.get("paidUntil").getAsLong() : 0L;
        boolean frozen = r.has("frozen") && r.get("frozen").getAsBoolean();
        return new Grant(token, paidUntil, frozen);
    }

    /** GET ping for diagnostics (is the server reachable at all). */
    public static boolean ping() {
        try {
            Http.get(base() + "/");
            return true; // any HTTP answer counts as alive
        } catch (Throwable t) {
            return false;
        }
    }

    /** POST JSON helper: non-2xx answers turn into a parseable {@link JsonObject} again (they carry error codes). */
    private static JsonObject post(String path, JsonObject body) throws IOException {
        try {
            return Http.postJson(base() + path, body);
        } catch (dev.nexus.launcher.net.HttpStatusException e) {
            try {
                JsonObject parsed = dev.nexus.launcher.core.Json.parse(e.body(), JsonObject.class);
                if (parsed != null) return parsed;
            } catch (Throwable ignored) { }
            throw new IOException("HTTP " + e.status() + " from " + base() + path);
        }
    }

    private static JsonObject requireOk(JsonObject r) throws IOException {
        if (r.has("ok") && r.get("ok").getAsBoolean()) return r;
        String code = r.has("error") ? r.get("error").getAsString() : "SERVER";
        Long paidUntil = r.has("paidUntil") && code.equals("EXPIRED") ? r.get("paidUntil").getAsLong() : null;
        throw new LicenseApiException(code, paidUntil);
    }
}
