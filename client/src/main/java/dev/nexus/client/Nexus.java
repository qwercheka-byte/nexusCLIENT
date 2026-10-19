package dev.nexus.client;

import dev.nexus.client.config.ConfigManager;
import dev.nexus.client.event.EventBus;
import dev.nexus.client.hud.HudManager;
import dev.nexus.client.hud.notification.NotificationManager;
import dev.nexus.client.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/** Service locator for the client's core systems. Initialised once from {@link NexusClient}. */
public final class Nexus {
    public static final String NAME = "NEXUS";
    /** Versions are read from the loaded mod metadata, so they always match the running jar and game. */
    public static final String VERSION = modVersion("nexus", "1.0.0");
    public static final String MC_VERSION = modVersion("minecraft", "unknown");

    private static EventBus events;
    private static ClientSettings settings;
    private static NotificationManager notifications;
    private static ModuleManager modules;
    private static ConfigManager config;
    private static HudManager hud;
    private static Session session;

    private Nexus() {}

    private static String modVersion(String id, String fallback) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(fallback);
    }

    static void init() {
        events = new EventBus();
        settings = new ClientSettings();
        notifications = new NotificationManager();
        modules = new ModuleManager();
        config = new ConfigManager();
        hud = new HudManager();
        session = new Session();
    }

    public static EventBus events() { return events; }
    public static ClientSettings settings() { return settings; }
    public static NotificationManager notifications() { return notifications; }
    public static ModuleManager modules() { return modules; }
    public static ConfigManager config() { return config; }
    public static HudManager hud() { return hud; }
    public static Session session() { return session; }

    /** Runs the task on the client thread (immediately if already there). */
    public static void runOnMain(Runnable task) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isOnThread()) task.run();
        else mc.execute(task);
    }
}
