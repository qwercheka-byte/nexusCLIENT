package dev.nexus.launcher.modrinth;

import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.game.LoaderType;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.net.Progress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Optimisation" set: well-known performance mods from Modrinth for Fabric/Quilt instances. They are third-party
 * mods (installed from Modrinth like any catalog mod, with required dependencies); the launcher only picks the set
 * and the newest version that matches the instance's Minecraft version. Mods without a build for that version are
 * skipped and reported.
 */
public final class PerformancePack {
    /** Modrinth project id, title, what it does (i18n key suffix). */
    public record Entry(String projectId, String title) {}

    public static final List<Entry> MODS = List.of(
            new Entry("AANobbMI", "Sodium"),          // rendering engine: much higher and steadier FPS
            new Entry("gvQqBUqZ", "Lithium"),         // game logic (ticks, AI, physics, chunk loading)
            new Entry("uXXizFIs", "FerriteCore"),     // memory usage
            new Entry("5ZwdcRci", "ImmediatelyFast"), // HUD, text, item and entity rendering
            new Entry("NNAgCjsB", "Entity Culling"),  // skips entities/block entities hidden behind walls
            new Entry("g96Z4WVZ", "BadOptimizations") // lightmap, sky and other small per-frame costs
    );

    public record Result(List<String> installed, List<String> alreadyPresent, List<String> unavailable, List<String> failed) {
        public boolean nothingDone() {
            return installed.isEmpty() && failed.isEmpty();
        }
    }

    private PerformancePack() {}

    public static boolean supports(LoaderType loader) {
        return loader == LoaderType.FABRIC || loader == LoaderType.QUILT;
    }

    /** Titles of the mods of the set that are installed in the instance. */
    public static List<String> present(Instance inst) {
        Set<String> ids = Modrinth.installedProjects(inst);
        List<String> out = new ArrayList<>();
        for (Entry e : MODS) if (ids.contains(e.projectId())) out.add(e.title());
        return out;
    }

    /** Installs every mod of the set that is missing (plus required dependencies). Network I/O: call off the FX thread. */
    public static Result install(Instance inst, Progress progress) throws IOException {
        List<String> installed = new ArrayList<>();
        List<String> present = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Set<String> ids = Modrinth.installedProjects(inst);
        for (Entry e : MODS) {
            progress.checkCancelled();
            if (ids.contains(e.projectId())) {
                present.add(e.title());
                continue;
            }
            try {
                if (Modrinth.latestCompatible(e.projectId(), inst) == null) {
                    unavailable.add(e.title());
                    continue;
                }
                installed.addAll(Modrinth.install(inst, e.projectId(), e.title(), progress));
                ids = Modrinth.installedProjects(inst);
            } catch (IOException ex) {
                if (progress.isCancelled()) throw ex;
                Log.warn("Performance mod " + e.title() + " failed: " + ex.getMessage());
                failed.add(e.title());
            }
        }
        Log.info("Performance pack for " + inst.name + ": installed " + installed + ", present " + present
                + ", unavailable " + unavailable + ", failed " + failed);
        return new Result(installed, present, unavailable, failed);
    }
}
