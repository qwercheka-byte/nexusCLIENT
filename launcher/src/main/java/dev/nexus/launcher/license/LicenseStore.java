package dev.nexus.launcher.license;

import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Local offline license cache ({@code <data>/license.dat}). Contents are AES-GCM encrypted with a key derived
 * from this PC's HWID, so copying the file to another machine renders it garbage and forces a fresh sign-in
 * (which will be rejected by the worker's HWID check). Policy knobs are kept here so they stay reviewable:
 * re-validate the token online every {@link #REVALIDATE_DAYS}, and allow fully offline play for up to
 * {@link #OFFLINE_GRACE_DAYS} since the last successful online check (never later than a cached paidUntil).
 */
public final class LicenseStore {
    public static final int REVALIDATE_DAYS = 3;
    public static final int OFFLINE_GRACE_DAYS = 14;
    private static final SecureRandom RNG = new SecureRandom();

    private LicenseStore() {}

    /** What the gate needs to know about this PC. */
    public static final class Snapshot {
        public String login = "";
        public String token = "";
        public long lastOnlineVerifyMs = 0;
        public long paidUntilMs = 0;

        public long offlineDeadline() {
            long grace = lastOnlineVerifyMs + OFFLINE_GRACE_DAYS * 86_400_000L;
            return paidUntilMs > 0 ? Math.min(grace, paidUntilMs) : grace;
        }

        public boolean needsReverify() {
            return System.currentTimeMillis() - lastOnlineVerifyMs > REVALIDATE_DAYS * 86_400_000L;
        }
    }

    public static Path file() {
        return LauncherDirs.root().resolve("license.dat");
    }

    public static Snapshot load() {
        Path f = file();
        if (!Files.exists(f)) return null;
        try {
            byte[] all = Base64.getMimeDecoder().decode(new String(Files.readAllBytes(f), StandardCharsets.UTF_8).trim());
            if (all.length < 12 + 16) return null; // nonce + tag at minimum
            byte[] nonce = new byte[12];
            System.arraycopy(all, 0, nonce, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] plain = c.doFinal(all, 12, all.length - 12);
            String json = new String(plain, StandardCharsets.UTF_8);
            Snapshot s = Json.parse(json, Snapshot.class);
            return (s.login == null || s.login.isBlank() || s.token == null || s.token.isBlank()) ? null : s;
        } catch (Exception e) {
            Log.info("license.dat unreadable (moved to another PC or tampered) — sign-in required");
            return null;
        }
    }

    public static void save(Snapshot s) {
        try {
            byte[] nonce = new byte[12];
            RNG.nextBytes(nonce);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] payload = Json.GSON.toJson(s).getBytes(StandardCharsets.UTF_8);
            byte[] enc = c.doFinal(payload);
            byte[] out = new byte[nonce.length + enc.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(enc, 0, out, nonce.length, enc.length);
            Files.write(file(), Base64.getMimeEncoder(64, "\n".getBytes()).encode(out));
        } catch (Exception e) {
            Log.warn("Cannot save license.dat", e);
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(file());
        } catch (Exception e) {
            Log.warn("Cannot delete license.dat", e);
        }
    }

    private static SecretKeySpec key() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] k = md.digest(("nexus-licensefile-v1\n" + Hwid.get()).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
