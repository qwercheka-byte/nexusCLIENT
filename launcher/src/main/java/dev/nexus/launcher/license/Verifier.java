package dev.nexus.launcher.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives the "verifier" that the launcher sends instead of the raw password.
 * The worker runs its own salted PBKDF2 over this string, so even reading the server DB
 * does not expose anything reusable for the user's real password, and the raw password
 * never crosses the wire or touches the disk.
 */
public final class Verifier {
    private Verifier() {}

    public static String of(String login, char[] password) {
        String material = "nexus-verifier-v1\n" + login.toLowerCase().trim() + '\n' + new String(password);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is guaranteed by the JCA spec
        } finally {
            // overwrite the copy of the password held in the material string is not reachable; zero ours
            java.util.Arrays.fill(password, '\0');
        }
    }
}
