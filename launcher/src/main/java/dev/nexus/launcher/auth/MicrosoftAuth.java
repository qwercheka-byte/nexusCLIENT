package dev.nexus.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.HttpStatusException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Microsoft account sign-in for Minecraft: OAuth 2.0 device code flow, then Xbox Live, XSTS and Minecraft
 * services. Requires an Azure application (client) ID that Mojang has approved for the Minecraft API.
 */
public final class MicrosoftAuth {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String XBL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_ENTITLEMENTS = "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    /** Response of the device authorization endpoint. */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {}

    private MicrosoftAuth() {}

    public static String clientId() throws AuthException {
        String id = Settings.get().microsoftClientId;
        if (id == null || id.isBlank()) throw new AuthException(I18n.t("auth.error.noClientId"));
        return id.trim();
    }

    public static DeviceCode startDeviceCode() throws IOException {
        Http.Response r = Http.postForm(AUTHORITY + "devicecode", Map.of("client_id", clientId(), "scope", SCOPE));
        JsonObject o = r.json();
        if (r.status() != 200) throw new AuthException(I18n.t("auth.error.microsoft", oauthError(o)));
        return new DeviceCode(o.get("device_code").getAsString(), o.get("user_code").getAsString(),
                o.get("verification_uri").getAsString(), o.get("expires_in").getAsInt(),
                o.has("interval") ? o.get("interval").getAsInt() : 5);
    }

    /** Polls until the user has entered the code, then completes the whole chain. */
    public static Account completeDeviceCode(DeviceCode code, BooleanSupplier cancelled, Consumer<String> status) throws IOException {
        String clientId = clientId();
        long deadline = System.currentTimeMillis() + code.expiresIn() * 1000L;
        int interval = Math.max(1, code.interval());
        while (true) {
            sleep(interval, cancelled);
            if (System.currentTimeMillis() > deadline) throw new AuthException(I18n.t("auth.error.expired"));
            Http.Response r = Http.postForm(AUTHORITY + "token", Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", code.deviceCode()));
            JsonObject o = r.json();
            if (r.status() == 200) {
                return finish(o.get("access_token").getAsString(), Json.str(o, "refresh_token", null), status);
            }
            String err = Json.str(o, "error", "");
            switch (err) {
                case "authorization_pending" -> { }
                case "slow_down" -> interval += 5;
                case "authorization_declined" -> throw new AuthException(I18n.t("auth.error.declined"));
                case "expired_token", "bad_verification_code" -> throw new AuthException(I18n.t("auth.error.expired"));
                default -> throw new AuthException(I18n.t("auth.error.microsoft", oauthError(o)));
            }
        }
    }

    /** Refreshes the Minecraft token when it expires within 10 minutes. Returns true when it was refreshed. */
    public static boolean ensureFresh(Account a) throws IOException {
        if (!a.isMicrosoft()) return false;
        if (a.mcAccessToken != null && a.mcTokenExpiresAt - System.currentTimeMillis() > 10 * 60_000L) return false;
        if (a.msRefreshToken == null) throw new AuthException(I18n.t("auth.error.relogin"));
        Http.Response r = Http.postForm(AUTHORITY + "token", Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId(),
                "refresh_token", a.msRefreshToken,
                "scope", SCOPE));
        JsonObject o = r.json();
        if (r.status() != 200) {
            Log.warn("Microsoft token refresh failed: " + oauthError(o));
            throw new AuthException(I18n.t("auth.error.relogin"));
        }
        Account fresh = finish(o.get("access_token").getAsString(), Json.str(o, "refresh_token", a.msRefreshToken), s -> { });
        a.mcAccessToken = fresh.mcAccessToken;
        a.mcTokenExpiresAt = fresh.mcTokenExpiresAt;
        a.msRefreshToken = fresh.msRefreshToken;
        a.name = fresh.name;
        a.skinUrl = fresh.skinUrl;
        return true;
    }

    private static Account finish(String msAccessToken, String refreshToken, Consumer<String> status) throws IOException {
        status.accept(I18n.t("auth.status.xbox"));
        JsonObject xblBody = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msAccessToken);
        xblBody.add("Properties", props);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = post(XBL, xblBody, "Xbox Live");
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        status.accept(I18n.t("auth.status.xsts"));
        JsonObject xstsBody = new JsonObject();
        JsonObject xp = new JsonObject();
        xp.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        xp.add("UserTokens", tokens);
        xstsBody.add("Properties", xp);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts;
        try {
            xsts = Http.postJson(XSTS, xstsBody);
        } catch (HttpStatusException e) {
            throw xstsError(e);
        }
        String xstsToken = xsts.get("Token").getAsString();

        status.accept(I18n.t("auth.status.minecraft"));
        JsonObject loginBody = new JsonObject();
        loginBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc;
        try {
            mc = Http.postJson(MC_LOGIN, loginBody);
        } catch (HttpStatusException e) {
            if (e.body().contains("Invalid app registration")) throw new AuthException(I18n.t("auth.error.appNotApproved"), e);
            throw new AuthException(I18n.t("auth.error.minecraft", e.status()), e);
        }
        String mcToken = mc.get("access_token").getAsString();
        long expiresIn = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400;
        Map<String, String> auth = Map.of("Authorization", "Bearer " + mcToken);

        status.accept(I18n.t("auth.status.profile"));
        JsonObject ent = Http.getJson(MC_ENTITLEMENTS, auth).getAsJsonObject();
        boolean owns = false;
        if (ent.has("items")) {
            for (JsonElement i : ent.getAsJsonArray("items")) {
                String n = Json.str(i.getAsJsonObject(), "name", "");
                if (n.equals("game_minecraft") || n.equals("product_minecraft")) owns = true;
            }
        }
        JsonObject profile;
        try {
            profile = Http.getJson(MC_PROFILE, auth).getAsJsonObject();
        } catch (HttpStatusException e) {
            if (e.status() == 404) throw new AuthException(I18n.t(owns ? "auth.error.noProfile" : "auth.error.noGame"), e);
            throw e;
        }
        Account a = new Account();
        a.type = Account.Type.MICROSOFT;
        a.uuid = Account.dashed(profile.get("id").getAsString());
        a.name = profile.get("name").getAsString();
        a.mcAccessToken = mcToken;
        a.mcTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        a.msRefreshToken = refreshToken;
        a.added = System.currentTimeMillis();
        if (profile.has("skins")) {
            for (JsonElement s : profile.getAsJsonArray("skins")) {
                JsonObject so = s.getAsJsonObject();
                if ("ACTIVE".equals(Json.str(so, "state", ""))) a.skinUrl = Json.str(so, "url", null);
            }
        }
        Log.info("Signed in Microsoft account " + a.name);
        return a;
    }

    private static JsonObject post(String url, JsonObject body, String what) throws IOException {
        try {
            return Http.postJson(url, body);
        } catch (HttpStatusException e) {
            throw new AuthException(I18n.t("auth.error.service", what, e.status()), e);
        }
    }

    private static AuthException xstsError(HttpStatusException e) {
        String key = "auth.error.xsts";
        try {
            JsonObject o = Json.parseObject(e.body());
            long code = o.has("XErr") ? o.get("XErr").getAsLong() : 0;
            if (code == 2148916233L) key = "auth.error.noXbox";
            else if (code == 2148916235L) key = "auth.error.xboxRegion";
            else if (code == 2148916236L || code == 2148916237L) key = "auth.error.adultVerify";
            else if (code == 2148916238L) key = "auth.error.child";
        } catch (RuntimeException ignored) {
            // generic message
        }
        return new AuthException(I18n.t(key), e);
    }

    private static String oauthError(JsonObject o) {
        String d = Json.str(o, "error_description", null);
        if (d != null) {
            int nl = d.indexOf('\n');
            if (nl > 0) d = d.substring(0, nl);
            int trace = d.indexOf(" Trace ID:");
            if (trace > 0) d = d.substring(0, trace);
            return d.trim();
        }
        return Json.str(o, "error", "unknown error");
    }

    private static void sleep(int seconds, BooleanSupplier cancelled) throws InterruptedIOException {
        long until = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < until) {
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("Cancelled");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Cancelled");
            }
        }
    }
}
