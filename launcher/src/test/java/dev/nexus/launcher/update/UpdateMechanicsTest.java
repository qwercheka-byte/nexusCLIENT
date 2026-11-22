package dev.nexus.launcher.update;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Smoke tests without a test framework; run with ./gradlew testUpdateMechanics. */
public final class UpdateMechanicsTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("nexus-updater-test-");
        try {
            starter(root.resolve("starter"));
            appSwap(root.resolve("swap"));
            manifestValidation();
            automaticHttps();
            System.out.println("Update mechanics tests passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void starter(Path root) throws Exception {
        Path app = Files.createDirectories(root.resolve("app"));
        Path source = app.resolve(WindowsStarter.BUNDLED_EXE);
        Path target = root.resolve(WindowsStarter.EXE_NAME);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "new executable");
        check(!WindowsStarter.refresh(app, 1, 0), "must not create a missing starter");
        Files.writeString(target, "old executable");
        try (FileChannel channel = FileChannel.open(root.resolve(".nexus-starter.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
            check(!WindowsStarter.refresh(app, 1, 0), "concurrent update must be skipped");
            check(Files.readString(target).equals("old executable"), "locked update changed the old starter");
        }
        check(!WindowsStarter.refresh(app, 0, 0), "no attempts should not replace the starter");
        check(Files.readString(target).equals("old executable"), "failed replacement changed the old starter");
        check(WindowsStarter.refresh(app, 1, 0), "starter replacement failed");
        check(Files.readString(target).equals("new executable"), "wrong starter contents");
        check(WindowsStarter.refresh(app, 1, 0), "identical starter should succeed");
        Files.delete(source);
        check(!WindowsStarter.refresh(app, 1, 0), "missing bundled starter should be skipped");
        check(Files.readString(target).equals("new executable"), "missing source damaged the starter");
    }

    private static void appSwap(Path root) throws Exception {
        Path app = Files.createDirectories(root.resolve("app"));
        Path fresh = Files.createDirectories(root.resolve("app.new"));
        Files.writeString(app.resolve("old.txt"), "old");
        check(!UpdateHelper.swap(app, fresh), "invalid package should be rejected");
        check(Files.exists(app.resolve("old.txt")), "invalid package damaged the old app");
        Path main = fresh.resolve("dev/nexus/launcher/Main.class");
        Files.createDirectories(main.getParent());
        Files.writeString(main, "new");
        Path bundled = fresh.resolve(WindowsStarter.BUNDLED_EXE);
        Files.createDirectories(bundled.getParent());
        Files.writeString(bundled, "new executable");
        Files.writeString(root.resolve(WindowsStarter.EXE_NAME), "old executable");
        check(UpdateHelper.swap(app, fresh), "app swap failed");
        check(Files.exists(app.resolve("dev/nexus/launcher/Main.class")), "new app missing");
        check(!Files.exists(root.resolve("app.old")), "old app not cleaned up");
        // An older helper only swaps app; the new app refreshes the starter after that first restart.
        check(WindowsStarter.refresh(app, 1, 0), "starter refresh after legacy app swap failed");
        check(Files.readString(root.resolve(WindowsStarter.EXE_NAME)).equals("new executable"), "legacy migration left old starter");
    }

    private static void manifestValidation() throws Exception {
        String hash = "a".repeat(64);
        String valid = "{\"launcher\":{\"version\":\"1.2.8\",\"files\":{\"win-app\":{\"url\":\"app.zip\",\"sha256\":\"" + hash + "\"}}}}";
        var parsed = UpdateManifest.parse(JsonParser.parseString(valid).getAsJsonObject(), "https://example.com/nexus-update.json");
        check(parsed.launcher().files().get("win-app").url().equals("https://example.com/app.zip"), "relative URL resolution");
        rejectManifest(valid.replace("1.2.8", "../outside"));
        rejectManifest(valid.replace("win-app", "../../outside"));
    }

    private static void automaticHttps() {
        String previous = System.getProperty("nexus.update.url");
        try {
            var secure = new UpdateManifest.FileRef("https://example.com/app.zip", "a".repeat(64), 1);
            var insecure = new UpdateManifest.FileRef("http://example.com/app.zip", "a".repeat(64), 1);
            var good = new Updates.Check(null, "1.2.8", secure, null, java.util.List.of());
            var bad = new Updates.Check(null, "1.2.8", insecure, null, java.util.List.of());
            System.setProperty("nexus.update.url", "https://example.com/nexus-update.json");
            check(Updates.safeForAutomaticInstall(good), "HTTPS update should be eligible for automatic installation");
            check(!Updates.safeForAutomaticInstall(bad), "HTTP package must not be installed automatically");
            System.setProperty("nexus.update.url", "http://example.com/nexus-update.json");
            check(!Updates.safeForAutomaticInstall(good), "HTTP manifest must not be installed automatically");
        } finally {
            if (previous == null) System.clearProperty("nexus.update.url");
            else System.setProperty("nexus.update.url", previous);
        }
    }

    private static void rejectManifest(String json) throws Exception {
        try {
            UpdateManifest.parse(JsonParser.parseString(json).getAsJsonObject(), "https://example.com/nexus-update.json");
            throw new AssertionError("unsafe manifest accepted");
        } catch (IOException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
