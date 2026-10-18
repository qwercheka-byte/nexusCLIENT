package dev.nexus.client.config;

import dev.nexus.client.util.Lang;
import com.google.gson.JsonObject;
import dev.nexus.client.Nexus;
import dev.nexus.client.NexusClient;
import dev.nexus.client.hud.notification.Notification;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Create / rename / duplicate / delete / switch / reload profiles.
 * All disk work runs on the IO lane; results are applied on the client thread.
 */
public final class ProfileManager {
    public static final String DEFAULT_PROFILE = "default";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_\\-][A-Za-z0-9 _\\-.]{0,22}[A-Za-z0-9_\\-]|[A-Za-z0-9_\\-]");

    private final ConfigManager config;
    private final List<ProfileInfo> profiles = new ArrayList<>();
    private final List<ProfileInfo> view = Collections.unmodifiableList(profiles);
    private String active = DEFAULT_PROFILE;
    private boolean busy;

    ProfileManager(ConfigManager config) {
        this.config = config;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public List<ProfileInfo> list() { return view; }
    public String getActive() { return active; }
    /** True while a switch/reload is in flight (UI disables actions). */
    public boolean isBusy() { return busy; }

    public boolean exists(String name) {
        return find(name) != null;
    }

    private ProfileInfo find(String name) {
        for (ProfileInfo p : profiles) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }

    Path fileOf(String name) {
        return config.profilesDir().resolve(name + ".json");
    }

    // ------------------------------------------------------------------ internal (used by ConfigManager)

    void setActiveInternal(String name) { active = name; }

    void addInternal(String name, long modified) {
        if (!exists(name)) profiles.add(new ProfileInfo(name, modified));
        sort();
    }

    void touchInternal(String name, long modified) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name().equals(name)) profiles.set(i, new ProfileInfo(name, modified));
        }
    }

    private void sort() {
        profiles.sort(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT)));
    }

    /** Lists profile files (blocking – startup / IO thread only). */
    void scanBlocking() {
        profiles.clear();
        profiles.addAll(scanFiles(config.profilesDir()));
        sort();
    }

    private static List<ProfileInfo> scanFiles(Path dir) {
        List<ProfileInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                String file = p.getFileName().toString();
                String name = file.substring(0, file.length() - 5);
                if (!isValidName(name)) continue;
                long modified;
                try {
                    modified = Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    modified = 0;
                }
                out.add(new ProfileInfo(name, modified));
            }
        } catch (IOException e) {
            NexusClient.LOGGER.error("Could not list profiles", e);
        }
        return out;
    }

    /** Reads + migrates a profile file (blocking). Returns null if missing or corrupt. */
    JsonObject readProfileBlocking(String name) {
        Path file = fileOf(name);
        ConfigIO.ReadResult res = ConfigIO.readJson(file, config.backupsDir());
        if (res.corrupt()) {
            Nexus.runOnMain(() -> Nexus.notifications().post("Profile reset",
                    Lang.fmt("'{0}' was damaged; a backup was saved", name), Notification.Type.WARNING));
        }
        JsonObject json = res.json();
        if (json == null) return null;
        int schema = ConfigMigrator.schemaOf(json);
        if (schema < ConfigMigrator.CURRENT) {
            ConfigIO.backup(file, config.backupsDir(), "v" + schema);
            if (ConfigMigrator.migrateProfile(json)) {
                try {
                    ConfigIO.writeJson(file, json);
                    NexusClient.LOGGER.info("Migrated profile '{}' from schema {} to {}", name, schema, ConfigMigrator.CURRENT);
                } catch (IOException e) {
                    NexusClient.LOGGER.error("Could not write migrated profile {}", name, e);
                }
            }
        } else if (schema > ConfigMigrator.CURRENT) {
            ConfigIO.backup(file, config.backupsDir(), "newer-v" + schema);
        }
        return json;
    }

    // ------------------------------------------------------------------ public operations

    /** Validates a new name, returns an error message or null if ok. */
    public String validateNewName(String name, String ignore) {
        if (name == null || name.isBlank()) return "Enter a name";
        String n = name.trim();
        if (!isValidName(n)) return "Use letters, digits, space, _ - . (max 24)";
        ProfileInfo existing = find(n);
        if (existing != null && (ignore == null || !existing.name().equalsIgnoreCase(ignore))) return "Name already used";
        return null;
    }

    /** Creates a profile from the current settings ({@code copyCurrent}) or from defaults. */
    public void create(String rawName, boolean copyCurrent, Consumer<String> onError) {
        String name = rawName.trim();
        String error = validateNewName(name, null);
        if (error != null) {
            onError.accept(error);
            return;
        }
        JsonObject json = copyCurrent ? config.snapshotProfile(name) : ConfigManager.emptyProfile(name);
        long modified = json.get("modified").getAsLong();
        addInternal(name, modified);
        Path file = fileOf(name);
        config.io().submit(() -> {
            ConfigIO.writeJson(file, json);
            return null;
        }).whenComplete((r, e) -> Nexus.runOnMain(() -> {
            if (e != null) {
                NexusClient.LOGGER.error("Could not create profile {}", name, e);
                profiles.removeIf(p -> p.name().equals(name));
                Nexus.notifications().post("Profile error", Lang.fmt("Could not create '{0}'", name), Notification.Type.ERROR);
            } else {
                Nexus.notifications().post("Profile created", name, Notification.Type.SUCCESS);
            }
        }));
    }

    public void rename(String oldName, String rawNewName, Consumer<String> onError) {
        String newName = rawNewName.trim();
        String error = validateNewName(newName, oldName);
        if (error != null) {
            onError.accept(error);
            return;
        }
        if (newName.equals(oldName) || busy) return;
        boolean wasActive = oldName.equals(active);
        // make sure the latest state of the active profile is on disk before moving it
        if (wasActive) config.flushAsync();
        Path from = fileOf(oldName), to = fileOf(newName);
        config.io().submit(() -> {
            JsonObject json = Files.exists(from) ? readProfileBlocking(oldName) : null;
            if (json == null) json = ConfigManager.emptyProfile(newName);
            json.addProperty("name", newName);
            ConfigIO.writeJson(to, json);
            if (!from.equals(to)) Files.deleteIfExists(from);
            return null;
        }).whenComplete((r, e) -> Nexus.runOnMain(() -> {
            if (e != null) {
                NexusClient.LOGGER.error("Could not rename profile {}", oldName, e);
                Nexus.notifications().post("Profile error", Lang.fmt("Could not rename '{0}'", oldName), Notification.Type.ERROR);
                return;
            }
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).name().equals(oldName)) profiles.set(i, new ProfileInfo(newName, System.currentTimeMillis()));
            }
            sort();
            if (oldName.equals(active)) {
                active = newName;
                config.markClientDirty();
            }
            Nexus.notifications().post("Profile renamed", oldName + " → " + newName, Notification.Type.SUCCESS);
        }));
    }

    public void duplicate(String source) {
        String base = source + "-copy";
        String name = base;
        for (int i = 2; exists(name) || !isValidName(name); i++) {
            name = (base.length() > 20 ? base.substring(0, 20) : base) + i;
            if (i > 99) return;
        }
        if (source.equals(active)) {
            create(name, true, err -> {});
            return;
        }
        final String target = name;
        addInternal(target, System.currentTimeMillis());
        config.io().submit(() -> {
            JsonObject json = readProfileBlocking(source);
            if (json == null) json = ConfigManager.emptyProfile(target);
            json.addProperty("name", target);
            ConfigIO.writeJson(fileOf(target), json);
            return null;
        }).whenComplete((r, e) -> Nexus.runOnMain(() -> {
            if (e != null) {
                profiles.removeIf(p -> p.name().equals(target));
                Nexus.notifications().post("Profile error", Lang.fmt("Could not duplicate '{0}'", source), Notification.Type.ERROR);
            } else {
                Nexus.notifications().post("Profile duplicated", target, Notification.Type.SUCCESS);
            }
        }));
    }

    public void delete(String name) {
        if (busy || profiles.size() <= 1) {
            Nexus.notifications().post("Not possible", "At least one profile must remain", Notification.Type.WARNING);
            return;
        }
        if (name.equals(active)) {
            // switch to another profile first, then delete
            String fallback = null;
            for (ProfileInfo p : profiles) if (!p.name().equals(name)) { fallback = p.name(); break; }
            if (fallback == null) return;
            switchTo(fallback, false);
        }
        profiles.removeIf(p -> p.name().equals(name));
        Path file = fileOf(name);
        config.io().submit(() -> {
            ConfigIO.backup(file, config.backupsDir(), "deleted");
            Files.deleteIfExists(file);
            return null;
        }).whenComplete((r, e) -> Nexus.runOnMain(() -> {
            if (e != null) {
                NexusClient.LOGGER.error("Could not delete profile {}", name, e);
                Nexus.notifications().post("Profile error", Lang.fmt("Could not delete '{0}'", name), Notification.Type.ERROR);
            } else {
                Nexus.notifications().post("Profile deleted", Lang.fmt("{0} (backup kept)", name), Notification.Type.INFO);
            }
        }));
    }

    public void switchTo(String name) {
        switchTo(name, true);
    }

    private void switchTo(String name, boolean saveCurrent) {
        if (busy || name.equals(active) || !exists(name)) return;
        busy = true;
        if (saveCurrent) {
            // snapshot now (main thread), write first on the lane, then read the target
            JsonObject current = config.snapshotProfile(active);
            Path currentFile = fileOf(active);
            config.io().submit(() -> {
                ConfigIO.writeJson(currentFile, current);
                return null;
            });
        }
        config.clearProfileDirty();
        loadAndApply(name, "Profile loaded");
    }

    /** Re-reads the active profile from disk, discarding unsaved changes. */
    public void reload() {
        if (busy) return;
        busy = true;
        config.clearProfileDirty();
        loadAndApply(active, "Profile reloaded");
    }

    private void loadAndApply(String name, String message) {
        config.io().submit(() -> readProfileBlocking(name)).whenComplete((json, e) -> Nexus.runOnMain(() -> {
            busy = false;
            if (e != null) {
                NexusClient.LOGGER.error("Could not load profile {}", name, e);
                Nexus.notifications().post("Profile error", Lang.fmt("Could not load '{0}'", name), Notification.Type.ERROR);
                return;
            }
            config.applyProfile(json);
            boolean changed = !active.equals(name);
            active = name;
            if (changed) config.markClientDirty();
            if (json == null) config.markProfileDirty(); // missing/corrupt -> write defaults
            Nexus.notifications().post(message, name, Notification.Type.SUCCESS);
        }));
    }

    /** Re-lists profile files from disk (e.g. after the user copied files into the folder). */
    public void rescan() {
        Path dir = config.profilesDir();
        config.io().submit(() -> scanFiles(dir)).whenComplete((list, e) -> Nexus.runOnMain(() -> {
            if (e != null || list == null) return;
            profiles.clear();
            profiles.addAll(list);
            if (!exists(active)) profiles.add(new ProfileInfo(active, System.currentTimeMillis()));
            sort();
        }));
    }
}
