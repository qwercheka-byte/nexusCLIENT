package dev.nexus.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/** A Minecraft account. Microsoft accounts carry tokens; offline accounts only a name. */
public final class Account {
    public enum Type { MICROSOFT, OFFLINE }

    public static final Pattern OFFLINE_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /** Profile UUID with dashes. */
    public String uuid;
    public String name;
    public Type type;
    public String mcAccessToken;
    public long mcTokenExpiresAt;
    public String msRefreshToken;
    public String skinUrl;
    public long added;

    public static Account offline(String name) {
        Account a = new Account();
        a.type = Type.OFFLINE;
        a.name = name;
        // Same algorithm as the vanilla server for offline-mode players
        a.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
        a.added = System.currentTimeMillis();
        return a;
    }

    public boolean isMicrosoft() {
        return type == Type.MICROSOFT;
    }

    /** UUID without dashes, as Minecraft expects it on the command line. */
    public String uuidNoDashes() {
        return uuid.replace("-", "");
    }

    public static String dashed(String undashed) {
        if (undashed.contains("-") || undashed.length() != 32) return undashed;
        return undashed.substring(0, 8) + "-" + undashed.substring(8, 12) + "-" + undashed.substring(12, 16) + "-"
                + undashed.substring(16, 20) + "-" + undashed.substring(20);
    }
}
