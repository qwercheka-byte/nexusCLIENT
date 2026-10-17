package dev.nexus.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nexus.client.Nexus;
import dev.nexus.client.NexusClient;
import dev.nexus.client.hud.notification.Notification;
import dev.nexus.client.module.Module;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns the configuration directory ({@code config/nexus}) and persists:
 * <ul>
 *     <li>{@code client.json} – global client settings, theme and the active profile name</li>
 *     <li>{@code profiles/<name>.json} – module states, settings, keybinds and HUD layout</li>
 * </ul>
 * Changes are debounced and written asynchronously; nothing touches the disk on the render thread
 * except the initial load during game start and the final save on shutdown.
 */
public final class ConfigManager {
    private static final int SAVE_DELAY_TICKS = 10;

    private final Path root = FabricLoader.getInstance().getConfigDir().resolve("nexus");
    private final Path profilesDir = root.resolve("profiles");
    private final Path backupsDir = root.resolve("backups");
    private final Path clientFile = root.resolve("client.json");
    private final ConfigIO io = new ConfigIO();
    private final ProfileManager profiles = new ProfileManager(this);

    private boolean loaded;
    private int suppress;
    private boolean clientDirty;
    private boolean profileDirty;
    private int dirtyTicks;

    // ------------------------------------------------------------------ loading

    /** Synchronous initial load, executed once while the game finishes starting. */
    public void loadInitial() {
        suppress++;
        boolean needsSave = false;
        try {
            ConfigIO.ReadResult res = ConfigIO.readJson(clientFile, backupsDir);
            JsonObject client = res.json();
            String active = ProfileManager.DEFAULT_PROFILE;
            if (res.corrupt()) {
                Nexus.notifications().post("Settings reset", "client.json was damaged, a backup was saved", Notification.Type.WARNING);
            }
            if (client != null) {
                int schema = ConfigMigrator.schemaOf(client);
                if (schema != ConfigMigrator.CURRENT && schema < ConfigMigrator.CURRENT) {
                    ConfigIO.backup(clientFile, backupsDir, "v" + schema);
                    needsSave = ConfigMigrator.migrateClient(client);
                } else if (schema > ConfigMigrator.CURRENT) {
                    ConfigIO.backup(clientFile, backupsDir, "newer-v" + schema);
                }
                active = string(client, "activeProfile", ProfileManager.DEFAULT_PROFILE);
                Nexus.settings().fromJson(object(client, "client"));
            } else {
                Nexus.settings().fromJson(null);
                needsSave = true;
            }

            profiles.scanBlocking();
            if (!ProfileManager.isValidName(active) || !profiles.exists(active)) {
                active = profiles.exists(ProfileManager.DEFAULT_PROFILE) || profiles.list().isEmpty()
                        ? ProfileManager.DEFAULT_PROFILE : profiles.list().get(0).name();
            }
            JsonObject profile = profiles.readProfileBlocking(active);
            if (profile == null) needsSave = true;
            applyProfile(profile);
            profiles.setActiveInternal(active);
            if (!profiles.exists(active)) profiles.addInternal(active, System.currentTimeMillis());
        } catch (RuntimeException e) {
            NexusClient.LOGGER.error("Unexpected error while loading configuration; using defaults", e);
            Nexus.notifications().post("Config error", "Defaults were loaded, see log for details", Notification.Type.ERROR);
            needsSave = false; // do not overwrite whatever is on disk
        } finally {
            suppress--;
        }
        loaded = true;
        if (needsSave) {
            clientDirty = true;
            profileDirty = true;
            flushAsync();
        }
        NexusClient.LOGGER.info("Configuration loaded (profile '{}')", profiles.getActive());
    }

    /** Applies a profile JSON to all modules. {@code null} = defaults. */
    void applyProfile(JsonObject profile) {
        suppress++;
        try {
            JsonObject modules = profile == null ? null : object(profile, "modules");
            for (Module m : Nexus.modules().all()) {
                JsonElement e = modules == null ? null : modules.get(m.getId());
                try {
                    m.fromJson(e != null && e.isJsonObject() ? e.getAsJsonObject() : null);
                } catch (RuntimeException ex) {
                    NexusClient.LOGGER.error("Failed to apply settings of module {}", m.getId(), ex);
                }
            }
        } finally {
            suppress--;
        }
    }

    // ------------------------------------------------------------------ snapshots (main thread)

    JsonObject snapshotProfile(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", ConfigMigrator.CURRENT);
        json.addProperty("name", name);
        json.addProperty("modified", System.currentTimeMillis());
        JsonObject modules = new JsonObject();
        for (Module m : Nexus.modules().all()) modules.add(m.getId(), m.toJson());
        json.add("modules", modules);
        return json;
    }

    static JsonObject emptyProfile(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", ConfigMigrator.CURRENT);
        json.addProperty("name", name);
        json.addProperty("modified", System.currentTimeMillis());
        json.add("modules", new JsonObject());
        return json;
    }

    private JsonObject snapshotClient() {
        JsonObject json = new JsonObject();
        json.addProperty("schema", ConfigMigrator.CURRENT);
        json.addProperty("activeProfile", profiles.getActive());
        json.add("client", Nexus.settings().toJson());
        return json;
    }

    // ------------------------------------------------------------------ dirty tracking & saving

    public void markClientDirty() {
        if (!loaded || suppress > 0) return;
        clientDirty = true;
        dirtyTicks = 0;
    }

    public void markProfileDirty() {
        if (!loaded || suppress > 0) return;
        profileDirty = true;
        dirtyTicks = 0;
    }

    /** Called every client tick; writes pending changes after a short quiet period. */
    public void tick() {
        if ((clientDirty || profileDirty) && ++dirtyTicks >= SAVE_DELAY_TICKS) flushAsync();
    }

    /** Snapshots dirty data on the calling (main) thread and writes it on the IO lane. */
    public void flushAsync() {
        if (!loaded) return;
        if (clientDirty) {
            clientDirty = false;
            JsonObject json = snapshotClient();
            io.submit(() -> {
                ConfigIO.writeJson(clientFile, json);
                return null;
            }).exceptionally(this::logWriteError);
        }
        if (profileDirty && !profiles.isBusy()) {
            profileDirty = false;
            String name = profiles.getActive();
            JsonObject json = snapshotProfile(name);
            Path file = profiles.fileOf(name);
            long modified = json.get("modified").getAsLong();
            io.submit(() -> {
                ConfigIO.writeJson(file, json);
                return null;
            }).exceptionally(this::logWriteError);
            profiles.touchInternal(name, modified);
        }
        dirtyTicks = 0;
    }

    private Void logWriteError(Throwable t) {
        NexusClient.LOGGER.error("Failed to write configuration", t);
        Nexus.runOnMain(() -> Nexus.notifications().post("Save failed", "Could not write config, see log", Notification.Type.ERROR));
        return null;
    }

    /** Final blocking save on shutdown. */
    public void saveBlocking() {
        if (!loaded) return;
        io.drain(3000);
        try {
            ConfigIO.writeJson(clientFile, snapshotClient());
            if (!profiles.isBusy()) ConfigIO.writeJson(profiles.fileOf(profiles.getActive()), snapshotProfile(profiles.getActive()));
            clientDirty = profileDirty = false;
        } catch (Exception e) {
            NexusClient.LOGGER.error("Failed to save configuration on shutdown", e);
        }
    }

    boolean hasPendingProfileChanges() {
        return profileDirty;
    }

    void clearProfileDirty() {
        profileDirty = false;
    }

    // ------------------------------------------------------------------ accessors

    public ProfileManager profiles() { return profiles; }
    public Path root() { return root; }
    Path profilesDir() { return profilesDir; }
    Path backupsDir() { return backupsDir; }
    ConfigIO io() { return io; }
    public boolean isLoaded() { return loaded; }

    public void ensureDirectories() {
        try {
            Files.createDirectories(profilesDir);
        } catch (Exception e) {
            NexusClient.LOGGER.error("Could not create config directory {}", profilesDir, e);
        }
    }

    // ------------------------------------------------------------------ json helpers

    static JsonObject object(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static String string(JsonObject parent, String key, String def) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : def;
    }
}
