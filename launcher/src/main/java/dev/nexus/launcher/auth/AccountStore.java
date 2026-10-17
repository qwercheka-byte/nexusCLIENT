package dev.nexus.launcher.auth;

import dev.nexus.launcher.core.FileUtil;
import dev.nexus.launcher.core.Json;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * accounts.json. Tokens are stored in plain JSON with owner-only permissions (POSIX) or inside the user's
 * profile (Windows); they are not encrypted.
 */
public final class AccountStore {
    private static final List<Account> accounts = new CopyOnWriteArrayList<>();

    private static final class FileData {
        int schema = 1;
        List<Account> accounts = new ArrayList<>();
    }

    private AccountStore() {}

    public static synchronized void load() {
        accounts.clear();
        Path file = LauncherDirs.accountsFile();
        if (!Files.exists(file)) return;
        try {
            FileData d = Json.read(file, FileData.class);
            if (d != null && d.accounts != null) {
                for (Account a : d.accounts) {
                    if (a != null && a.uuid != null && a.name != null && a.type != null) accounts.add(a);
                }
            }
        } catch (IOException e) {
            Log.warn("accounts.json is corrupted, keeping a backup", e);
            try {
                Files.move(file, file.resolveSibling("accounts.corrupt-" + System.currentTimeMillis() + ".json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    public static synchronized void save() {
        FileData d = new FileData();
        d.accounts.addAll(accounts);
        try {
            Json.write(LauncherDirs.accountsFile(), d);
            FileUtil.ownerOnly(LauncherDirs.accountsFile());
        } catch (IOException e) {
            Log.error("Cannot save accounts", e);
        }
    }

    public static List<Account> all() {
        return List.copyOf(accounts);
    }

    /** Adds or replaces (same UUID and type) an account and makes it active. */
    public static synchronized void put(Account a) {
        accounts.removeIf(x -> x.uuid.equals(a.uuid) && x.type == a.type);
        accounts.add(a);
        Settings.get().activeAccount = key(a);
        Settings.save();
        save();
    }

    public static synchronized void remove(Account a) {
        accounts.remove(a);
        if (key(a).equals(Settings.get().activeAccount)) {
            Settings.get().activeAccount = accounts.isEmpty() ? "" : key(accounts.get(0));
            Settings.save();
        }
        save();
    }

    public static Account active() {
        String k = Settings.get().activeAccount;
        for (Account a : accounts) if (key(a).equals(k)) return a;
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    public static void setActive(Account a) {
        Settings.get().activeAccount = key(a);
        Settings.save();
    }

    public static String key(Account a) {
        return a.type.name().toLowerCase(java.util.Locale.ROOT) + ":" + a.uuid;
    }
}
