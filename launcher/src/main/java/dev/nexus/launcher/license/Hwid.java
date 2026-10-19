package dev.nexus.launcher.license;

import dev.nexus.launcher.core.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * Windows machine fingerprint: SHA-256 over the MachineGuid (registry, survives reinstalls of Windows only)
 * mixed with the C: volume serial number (survives Windows reinstall, changes on disk swap) — the pair makes
 * the id stable for a normal user but unique per PC. On other OSes (dev machines) falls back to host+user.
 */
public final class Hwid {
    private static volatile String cached;

    private Hwid() {}

    public static String get() {
        String v = cached;
        if (v == null) {
            v = compute();
            cached = v;
        }
        return v;
    }

    private static String compute() {
        StringBuilder material = new StringBuilder("nexus-hwid-v1\n");
        String guid = "";
        String volume = "";
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            guid = runQuery(new String[]{"reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"}, "MachineGuid");
            volume = volumeSerial();
        }
        if (guid.isEmpty() && volume.isEmpty()) {
            // dev / non-Windows fallback
            String host = safe(System.getenv("COMPUTERNAME"));
            if (host.isEmpty()) host = safe(System.getProperty("user.name"));
            material.append("fallback\n").append(host).append('\n').append(safe(System.getProperty("os.name")));
        } else {
            material.append(guid).append('\n').append(volume);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(material.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** C: file-system serial from PowerShell; falls back to parsing `vol c:` output. */
    private static String volumeSerial() {
        String ps = runQuery(new String[]{"powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-Volume -DriveLetter C -ErrorAction SilentlyContinue).FileSystemSerialNumber"}, "");
        if (!ps.isEmpty() && ps.matches("[0-9A-Fa-f]{8}")) return ps.toUpperCase();
        String vol = runQuery(new String[]{"cmd", "/c", "vol", "c:"}, "");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:Serial Number is|номер тома:?\\s*|номер серийного тома:?\\s*)([0-9A-F]{4}-[0-9A-F]{4})",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)
                .matcher(vol);
        return m.find() ? m.group(1).replace("-", "") : "";
    }

    private static String runQuery(String[] cmd, String key) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            for (String line : out.split("\\R")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (!key.isEmpty() && !line.contains(key)) continue;
                if (!key.isEmpty()) {
                    String[] parts = line.split("\\s+");
                    String last = parts[parts.length - 1];
                    if (!last.equalsIgnoreCase(key)) return last;
                } else return line;
            }
        } catch (Exception e) {
            Log.warn("HWID probe failed (\"+cmd[0]+\"): " + e);
        }
        return "";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
