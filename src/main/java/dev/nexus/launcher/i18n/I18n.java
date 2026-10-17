package dev.nexus.launcher.i18n;

import dev.nexus.launcher.core.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Translations from {@code messages_<lang>.properties} (UTF-8). Missing keys fall back to English and then to
 * the key itself, so a forgotten translation is visible but never crashes the UI.
 * Placeholders are {0}, {1}, ... (plain replacement, no MessageFormat quoting rules).
 */
public final class I18n {
    private static final Properties fallback = load("en");
    private static Properties current = fallback;
    private static String language = "en";

    private I18n() {}

    public static void setLanguage(String lang) {
        language = lang;
        current = lang.equals("en") ? fallback : load(lang);
    }

    public static String language() {
        return language;
    }

    public static String t(String key, Object... args) {
        String s = current.getProperty(key);
        if (s == null) s = fallback.getProperty(key);
        if (s == null) return key;
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    public static boolean has(String key) {
        return current.containsKey(key) || fallback.containsKey(key);
    }

    private static Properties load(String lang) {
        Properties p = new Properties();
        String path = "/dev/nexus/launcher/i18n/messages_" + lang + ".properties";
        try (InputStream in = I18n.class.getResourceAsStream(path)) {
            if (in == null) {
                Log.warn("Missing translation file " + path);
            } else {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.warn("Cannot read " + path, e);
        }
        return p;
    }
}
