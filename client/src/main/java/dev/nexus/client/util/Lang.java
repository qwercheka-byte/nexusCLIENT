package dev.nexus.client.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nexus.client.NexusClient;
import net.minecraft.client.MinecraftClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Translations of the NEXUS interface.
 * <p>
 * The source strings in the code are English; a translation file maps each English string to its translation
 * ({@code assets/nexus/i18n/<lang>.json}, gettext style: the English text is the key). The language follows the
 * Minecraft language setting – Russian when the game is set to any {@code ru_*} language, English otherwise – and
 * switches immediately when the game language changes.
 * <p>
 * {@link dev.nexus.client.render.TextRender} translates every plain string it draws or measures, so static labels
 * need no extra code. Strings that are composed at runtime ("3 of 15 enabled") use {@link #fmt} with a template that
 * is translated as a whole. User input (text fields, profile names typed by the player) is drawn with the raw
 * methods and is never translated.
 */
public final class Lang {
    private static final Map<String, Map<String, String>> FILES = new HashMap<>();
    private static String code = "";
    private static Map<String, String> current = Map.of();
    private static int revision;

    private Lang() {}

    /** Translation of an English UI string, or the string itself when there is none. */
    public static String tr(String english) {
        if (english == null || english.isEmpty()) return english;
        Map<String, String> m = map();
        if (m.isEmpty()) return english;
        String t = m.get(english);
        return t != null ? t : english;
    }

    /** Translates the template, then replaces {0}, {1}, … with the arguments (which are not translated). */
    public static String fmt(String englishTemplate, Object... args) {
        String s = tr(englishTemplate);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }

    /** Changes whenever the effective language changes; lets callers invalidate cached, translated strings. */
    public static int revision() {
        map();
        return revision;
    }

    public static boolean isRussian() {
        return !map().isEmpty() && code.startsWith("ru");
    }

    private static Map<String, String> map() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String lang = mc == null || mc.options == null || mc.options.language == null ? "en_us" : mc.options.language;
        if (!lang.equals(code)) {
            code = lang;
            current = load(fileFor(lang));
            revision++;
        }
        return current;
    }

    private static String fileFor(String lang) {
        String l = lang.toLowerCase(Locale.ROOT);
        if (l.startsWith("ru")) return "ru";
        return "";
    }

    private static Map<String, String> load(String file) {
        if (file.isEmpty()) return Map.of();
        return FILES.computeIfAbsent(file, f -> {
            String path = "/assets/nexus/i18n/" + f + ".json";
            Map<String, String> out = new HashMap<>();
            try (InputStream in = Lang.class.getResourceAsStream(path)) {
                if (in == null) {
                    NexusClient.LOGGER.warn("Missing translation file {}", path);
                    return Map.of();
                }
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getKey().startsWith("//") || !e.getValue().isJsonPrimitive()) continue;
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (Exception e) {
                NexusClient.LOGGER.warn("Cannot read translation file {}: {}", path, e.toString());
                return Map.of();
            }
            return Map.copyOf(out);
        });
    }
}
