package dev.nexus.launcher.assist;

import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.instance.ModManager;
import dev.nexus.launcher.instance.ModManager.ModFile;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Build doctor": offline diagnostics for a single instance. Scans the mods folder for duplicates, conflicts
 * and wrong-loader jars, evaluates the RAM budget against the mod count and the machine, and reads the last
 * game log for well-known failures. Each finding can carry a runnable one-click fix.
 *
 * <p>Everything here is deterministic and free — no network and no API keys. A future cloud LLM layer can sit
 * on top of these findings as context; the checks below stay the always-available fallback.</p>
 */
public final class BuildDoctor {

    public enum Level { ERROR, WARN, INFO }

    /** A one-click remedy shown as a button on the finding card. Runs off the FX thread. */
    public record Fix(String label, Callable<String> action) {}

    public record Finding(Level level, String title, String text, Fix fix) {
        public Finding(Level level, String title, String text) {
            this(level, title, text, null);
        }
    }

    private BuildDoctor() {}

    /* ------------------------------------------------ public entry ------------------------------------------------ */

    public static List<Finding> check(Instance inst) {
        List<Finding> out = new ArrayList<>();
        List<ModFile> mods = ModManager.list(inst);
        ramCheck(inst, mods.size(), out);
        duplicates(mods, out);
        fabricApiMissing(inst, mods, out);
        conflicts(mods, out);
        logScan(inst, out);
        if (out.isEmpty()) out.add(new Finding(Level.INFO, I18n.t("doctor.clean"), I18n.t("doctor.cleanText")));
        return out;
    }

    /* ------------------------------------------------ individual checks ------------------------------------------------ */

    /** Mod-count-based memory budget, clamped to what the machine can actually spare. */
    static int recommendedRamMb(int modCount, long totalRamMb) {
        int rec = modCount < 10 ? 2048 : modCount < 30 ? 3072 : modCount < 80 ? 4096 : 6144;
        if (totalRamMb > 0) rec = (int) Math.min(rec, Math.max(1024L, totalRamMb - 3072));
        return rec;
    }

    private static void ramCheck(Instance inst, int modCount, List<Finding> out) {
        int total = (int) OS.totalMemoryMb();
        int eff = inst.maxMemoryMb != null ? inst.maxMemoryMb : Settings.get().maxMemoryMb;
        int rec = recommendedRamMb(modCount, total);
        if (eff < rec) {
            out.add(new Finding(Level.ERROR, I18n.t("doctor.ramTitle"),
                    I18n.t("doctor.ramText", eff, modCount, rec),
                    new Fix(I18n.t("doctor.ramFix", rec), () -> setRam(inst, rec))));
        } else if (total > 0 && eff > total - 2048) {
            int suggest = Math.max(4096, (int) total - 4096);
            out.add(new Finding(Level.WARN, I18n.t("doctor.ramHighTitle"),
                    I18n.t("doctor.ramHighText", eff, suggest),
                    new Fix(I18n.t("doctor.ramFix", suggest), () -> setRam(inst, suggest))));
        }
    }

    private static String setRam(Instance inst, int mb) throws IOException {
        inst.minMemoryMb = 512;
        inst.maxMemoryMb = mb;
        InstanceManager.save(inst);
        return I18n.t("doctor.ramDone", mb);
    }

    private static void duplicates(List<ModFile> mods, List<Finding> out) {
        Map<String, List<ModFile>> byId = new LinkedHashMap<>();
        for (ModFile m : mods) {
            if (m.id() != null) byId.computeIfAbsent(m.id(), k -> new ArrayList<>()).add(m);
        }
        for (var e : byId.entrySet()) {
            if (e.getValue().size() < 2) continue;
            ModFile keep = e.getValue().get(0);
            List<ModFile> dupes = new ArrayList<>(e.getValue());
            dupes.remove(keep);
            for (ModFile d : dupes) {
                out.add(new Finding(Level.WARN, I18n.t("doctor.dupTitle"),
                        I18n.t("doctor.dupText", e.getKey(), keep.fileName(), d.fileName()),
                        new Fix(I18n.t("doctor.dupFix", d.fileName()), () -> {
                            Files.delete(d.file());
                            return I18n.t("doctor.deleteDone", d.fileName());
                        })));
            }
        }
    }

    private static final Set<String> FAPI_IDS = java.util.Set.of("fabric-api", "fabric-api-blocks", "ffapi", "fabric");

    private static void fabricApiMissing(Instance inst, List<ModFile> mods, List<Finding> out) {
        if (inst.loaderType() == null) return;
        if (inst.loaderType() != dev.nexus.launcher.game.LoaderType.FABRIC
                && inst.loaderType() != dev.nexus.launcher.game.LoaderType.QUILT) return;
        boolean anyFabric = false;
        boolean hasApi = false;
        for (ModFile m : mods) {
            if (m.id() == null) continue;
            anyFabric = true;
            if (FAPI_IDS.contains(m.id())) hasApi = true;
        }
        if (anyFabric && !hasApi && !mods.isEmpty()) {
            out.add(new Finding(Level.ERROR, I18n.t("doctor.fapiTitle"), I18n.t("doctor.fapiText"),
                    new Fix(I18n.t("doctor.fapiFix"), () -> {
                        List<String> r = dev.nexus.launcher.modrinth.Modrinth.install(inst, "P7dR8mSH", "Fabric API",
                                new dev.nexus.launcher.net.Progress());
                        return I18n.t("doctor.installed", String.join(", ", r));
                    })));
        }
    }

    private static void conflicts(List<ModFile> mods, List<Finding> out) {
        Map<String, ModFile> byId = new LinkedHashMap<>();
        for (ModFile m : mods) if (m.id() != null) byId.put(m.id(), m);
        String[][] pairs = {
                {"optifine", "sodium", "OptiFine", "Sodium", I18n.t("doctor.optiSodium")},
                {"optifine", "iris", "OptiFine", "Iris", I18n.t("doctor.optiIris")},
                {"optifine", "lithium", "OptiFine", "Lithium", I18n.t("doctor.optiLithium")},
        };
        for (String[] p : pairs) {
            ModFile a = byId.get(p[0]);
            ModFile b = byId.get(p[1]);
            if (a != null && b != null) {
                out.add(new Finding(Level.ERROR, I18n.t("doctor.conflictTitle"),
                        I18n.t("doctor.conflictText", p[2], p[3], p[4]),
                        new Fix(I18n.t("doctor.conflictFix", p[2]), () -> {
                            Files.delete(a.file());
                            return I18n.t("doctor.deleteDone", a.fileName());
                        })));
            }
        }
    }

    /* ------------------------------------------------ log scanning ------------------------------------------------ */

    private static final int LOG_TAIL = 96 * 1024;

    private static final Pattern OOM = Pattern.compile("OutOfMemoryError");
    private static final Pattern REQUIRES = Pattern.compile("(?:requires|\\bis incompatible with\\b)[^\\n]*(?:\\{[^}]*\\}[^\\n]*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXIN = Pattern.compile("(Mixin apply failed|ClassNotFoundException|NoSuchMethodError|NoClassDefFoundError)[^\\n]*");
    private static final Pattern GLFW = Pattern.compile("GLFW error 65543[^\\n]*");

    private static void logScan(Instance inst, List<Finding> out) {
        Path log = inst.gameDir().resolve("logs").resolve("latest.log");
        if (!Files.isRegularFile(log)) {
            return;
        }
        String text = tail(log);
        // OutOfMemoryError first — it usually makes the rest noise
        if (OOM.matcher(text).find()) {
            int total = (int) OS.totalMemoryMb();
            int rec = recommendedRamMb(ModManager.list(inst).size(), total);
            out.add(new Finding(Level.ERROR, I18n.t("doctor.oomTitle"), I18n.t("doctor.oomText", rec),
                    new Fix(I18n.t("doctor.ramFix", rec), () -> setRam(inst, rec))));
        }
        if (text.contains("Incompatible mods found")) {
            List<String> lines = new ArrayList<>();
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.startsWith("- ") || t.toLowerCase().contains("requires ") || t.toLowerCase().contains("is incompatible with")) {
                    if (lines.size() < 3 && t.length() > 8) lines.add(t);
                }
            }
            if (!lines.isEmpty()) {
                out.add(new Finding(Level.ERROR, I18n.t("doctor.incompatTitle"),
                        I18n.t("doctor.incompatText", String.join("\n", lines))));
            }
        }
        Matcher mx = MIXIN.matcher(text);
        if (mx.find()) {
            String line = mx.group(0);
            line = line.length() > 160 ? line.substring(0, 157) + "…" : line;
            out.add(new Finding(Level.ERROR, I18n.t("doctor.mixinTitle"), I18n.t("doctor.mixinText", line)));
        }
        if (text.contains("GLFW error 65543")) {
            out.add(new Finding(Level.ERROR, I18n.t("doctor.glfwTitle"), I18n.t("doctor.glfwText")));
        }
    }

    private static String tail(Path file) {
        try {
            byte[] all = Files.readAllBytes(file);
            int start = Math.max(0, all.length - LOG_TAIL);
            return new String(all, start, all.length - start, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
