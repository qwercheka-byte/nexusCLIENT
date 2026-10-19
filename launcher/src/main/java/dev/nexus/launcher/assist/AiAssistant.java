package dev.nexus.launcher.assist;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.ModManager;
import dev.nexus.launcher.instance.ModManager.ModFile;
import dev.nexus.launcher.net.Http;
import dev.nexus.launcher.net.HttpStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Optional cloud layer of the assistant. Talks to an OpenAI-compatible chat-completions API (Groq by default,
 * OpenRouter as an option) using the player's own free API key. The key is stored locally in settings.json and
 * is sent only to the chosen provider. No telemetry, no proxies, no hidden quotas — everything rides on the
 * provider's free tier. Requires the player's explicit one-time consent because the build context (mod list
 * and the tail of the launch log) is uploaded with every question.
 */
public final class AiAssistant {

    /** Supported providers of an OpenAI-compatible chat API with a free tier. */
    public enum Provider {
        DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions",
                "deepseek-flash", "https://platform.deepseek.com/api_keys", "sk-"),
        GROQ("groq", "Groq", "https://api.groq.com/openai/v1/chat/completions",
                "llama-3.3-70b-versatile", "https://console.groq.com/keys", "gsk_"),
        OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/chat/completions",
                "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/keys", "sk-or-");

        public final String id;
        public final String title;
        public final String endpoint;
        public final String defaultModel;
        public final String keysUrl;
        public final String keyPrefix;

        Provider(String id, String title, String endpoint, String defaultModel, String keysUrl, String keyPrefix) {
            this.id = id;
            this.title = title;
            this.endpoint = endpoint;
            this.defaultModel = defaultModel;
            this.keysUrl = keysUrl;
            this.keyPrefix = keyPrefix;
        }

        public static Provider ofId(String id) {
            for (Provider p : values()) if (p.id.equals(id)) return p;
            return DEEPSEEK;
        }
    }

    private AiAssistant() { }

    public static boolean connected() {
        Settings s = Settings.get();
        return s.aiConsent && s.aiKey != null && !s.aiKey.isBlank();
    }

    public static Provider provider() {
        return Provider.ofId(Settings.get().aiProvider);
    }

    /** Attempts to guess the provider from the key shape; falls back to Groq. */
    public static Provider detect(String key) {
        String k = key.trim();
        if (k.startsWith(Provider.OPENROUTER.keyPrefix)) return Provider.OPENROUTER;
        if (k.startsWith(Provider.GROQ.keyPrefix)) return Provider.GROQ;
        if (k.startsWith(Provider.DEEPSEEK.keyPrefix)) return Provider.DEEPSEEK;
        return Provider.DEEPSEEK;
    }

    /**
     * Checks the key with a minimal completion and, if valid, persists provider/key/model and consent into
     * settings. Throws IOException with a readable message on auth failure, quota or network problems.
     */
    public static void connect(String key, Provider p) throws IOException {
        String k = key.trim();
        if (k.isEmpty()) throw new IOException(I18n.t("ai.noKey"));
        complete(k, p, p.defaultModel, List.of(
                msg("user", "Reply with exactly: ok")));
        Settings s = Settings.get();
        s.aiProvider = p.id;
        s.aiKey = k;
        s.aiModel = p.defaultModel;
        s.aiConsent = true;
        Settings.save();
    }

    public static void disconnect() {
        Settings s = Settings.get();
        s.aiKey = "";
        s.aiConsent = false;
        Settings.save();
    }

    /**
     * Asks the model about the player's build. A context block (instance, mods, RAM findings, log tail)
     * is prepended to the question; the model answers in the launcher language.
     */
    public static String ask(Instance inst, String question, String doctorSummary) throws IOException {
        Settings s = Settings.get();
        Provider p = provider();
        String model = s.aiModel == null || s.aiModel.isBlank() ? p.defaultModel : s.aiModel;
        return complete(s.aiKey, p, model, List.of(
                msg("system", systemPrompt(inst, doctorSummary)),
                msg("user", question)));
    }

    /** Builds the shared context once so the doctor summary and the system prompt stay consistent. */
    public static String systemPrompt(Instance inst, String doctorSummary) {
        boolean ru = I18n.language().startsWith("ru");
        StringBuilder b = new StringBuilder(4096);
        b.append(ru
                ? "Ты — помощник в лаунчере NEXUS для Minecraft (Java + Fabric). Помогаешь только с темами " +
                  "сборок: моды, советы по производительности, краши и логи, оперативная память, версии. " +
                  "Отвечай по-русски, коротко и по делу; порядок действий нумеруй. Никогда не выдумывай " +
                  "несуществующие моды — советуй проверенные (Sodium, Lithium, Iris, FerriteCore, ModernFix и др.).\n\n"
                : "You are an assistant in the NEXUS launcher for Minecraft (Java + Fabric). Help only with " +
                  "build topics: mods, performance advice, crashes and logs, RAM, versions. Answer in English, " +
                  "short and to the point; number the steps. Never invent mods — recommend proven ones " +
                  "(Sodium, Lithium, Iris, FerriteCore, ModernFix, etc.).\n\n");
        if (inst != null) {
            b.append(ru ? "Сборка игрока:\n" : "Player's build:\n");
            b.append("- ").append(inst.name).append(" — Minecraft ").append(inst.mcVersion)
                    .append(", ").append(inst.loader).append('\n');
            int ram = inst.maxMemoryMb != null ? inst.maxMemoryMb : Settings.get().maxMemoryMb;
            long total = OS.totalMemoryMb();
            b.append(ru ? "- Память: выделено " : "- RAM: assigned ").append(ram)
                    .append(ru ? " МБ, всего в системе " : " MB, system total ").append(total).append(" MB\n");
            List<ModFile> mods = ModManager.list(inst);
            if (mods.isEmpty()) {
                b.append(ru ? "- Модов нет (mods пуст)\n" : "- No mods installed\n");
            } else {
                b.append(ru ? "- Моды (" : "- Mods (").append(mods.size()).append("):\n");
                int shown = 0;
                for (ModFile m : mods) {
                    if (++shown > 120) { b.append("  …").append(ru ? " и ещё " : " and ").append(mods.size() - 120).append(" more\n"); break; }
                    if (!m.enabled()) continue;
                    b.append("  • ").append(m.name() != null && !m.name().isBlank() ? m.name() : m.fileName())
                            .append(m.version() != null && !m.version().isBlank() ? " " + m.version() : "")
                            .append('\n');
                }
            }
        }
        if (doctorSummary != null && !doctorSummary.isBlank()) {
            b.append('\n').append(ru ? "Что уже нашёл встроенный офлайн-доктор:\n" : "The built-in offline doctor already found:\n")
                    .append(doctorSummary).append('\n');
        }
        if (inst != null) {
            Path log = inst.gameDir().resolve("logs").resolve("latest.log");
            if (Files.isRegularFile(log)) {
                String tail = tailOf(log, 12000);
                if (!tail.isBlank()) {
                    b.append('\n').append(ru ? "Хвост последнего лога запуска:\n" : "Tail of the last launch log:\n")
                            .append("```\n").append(tail).append("\n```\n");
                }
            }
        }
        return b.toString();
    }

    /* ------------------------------------------------- HTTP ------------------------------------------------- */

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static String complete(String key, Provider p, String model, List<JsonObject> messages) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.4);
        body.addProperty("max_tokens", 1400);
        JsonArray arr = new JsonArray();
        messages.forEach(arr::add);
        body.add("messages", arr);
        try {
            java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + key);
            JsonObject resp = postJson(p.endpoint, body, headers);
            JsonObject choice = resp.getAsJsonArray("choices").get(0).getAsJsonObject();
            return choice.getAsJsonObject("message").get("content").getAsString().trim();
        } catch (HttpStatusException e) {
            if (e.status() == 401 || e.status() == 403) throw new IOException(I18n.t("ai.badKey"));
            if (e.status() == 402) throw new IOException(I18n.t("ai.noBalance"));
            if (e.status() == 429) throw new IOException(I18n.t("ai.quota"));
            throw new IOException(I18n.t("ai.httpError", e.status()));
        }
    }

    /** Same as {@link Http#postJson} but with headers (Authorization). */
    private static JsonObject postJson(String url, JsonObject body, java.util.Map<String, String> headers) throws IOException {
        java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(75))
                .header("User-Agent", dev.nexus.launcher.core.BuildInfo.userAgent())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        headers.forEach(b::header);
        java.net.http.HttpRequest req = b.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build();
        try {
            java.net.http.HttpResponse<String> r = java.net.http.HttpClient.newHttpClient()
                    .send(req, java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) throw new HttpStatusException(r.statusCode(), url, r.body());
            return Json.parseObject(r.body());
        } catch (java.net.http.HttpConnectTimeoutException | java.net.SocketTimeoutException e) {
            throw new IOException(I18n.t("ai.timeout"), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(I18n.t("ai.timeout"), e);
        } catch (java.net.ConnectException | java.net.UnknownHostException e) {
            throw new IOException(I18n.t("ai.offline"), e);
        }
    }

    private static String tailOf(Path file, int max) {
        try {
            byte[] data = Files.readAllBytes(file);
            int from = Math.max(0, data.length - (max * 4)); // UTF-8 worst case
            String s = new String(data, from, data.length - from, java.nio.charset.StandardCharsets.UTF_8);
            if (s.length() > max) s = s.substring(s.length() - max);
            int nl = s.indexOf('\n');
            return nl >= 0 && nl < s.length() - 1 ? s.substring(nl + 1) : s;
        } catch (IOException e) {
            return "";
        }
    }
}
