package dev.nexus.client;

import dev.nexus.client.event.events.AttackEntityEvent;
import dev.nexus.client.event.events.KeyEvent;
import dev.nexus.client.event.events.MouseButtonEvent;
import dev.nexus.client.event.events.TickEvent;
import dev.nexus.client.event.events.WorldJoinEvent;
import dev.nexus.client.event.events.WorldLeaveEvent;
import dev.nexus.client.module.ModuleManager;
import dev.nexus.client.module.impl.hud.ArmorHudModule;
import dev.nexus.client.module.impl.hud.CoordinatesHudModule;
import dev.nexus.client.module.impl.hud.FpsHudModule;
import dev.nexus.client.module.impl.hud.KeystrokesModule;
import dev.nexus.client.module.impl.hud.ModuleListHudModule;
import dev.nexus.client.module.impl.hud.PotionHudModule;
import dev.nexus.client.module.impl.hud.TargetHudModule;
import dev.nexus.client.module.impl.hud.WorldTimeHudModule;
import dev.nexus.client.module.impl.interfaces.NotificationsModule;
import dev.nexus.client.module.impl.performance.UnfocusedFpsModule;
import dev.nexus.client.module.impl.utility.AutoSprintModule;
import dev.nexus.client.module.impl.utility.ZoomModule;
import dev.nexus.client.module.impl.visuals.CustomCrosshairModule;
import dev.nexus.client.module.impl.visuals.CustomNametagsModule;
import dev.nexus.client.module.impl.visuals.FullbrightModule;
import dev.nexus.client.ui.NexusScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric client entrypoint: wires the core systems to game events. */
public final class NexusClient implements ClientModInitializer {
    public static final String MOD_ID = "nexus";
    public static final Logger LOGGER = LoggerFactory.getLogger("NEXUS");

    @Override
    public void onInitializeClient() {
        long start = System.nanoTime();
        Nexus.init();
        registerModules(Nexus.modules());
        Nexus.modules().init();
        Nexus.notifications().init();
        Nexus.config().ensureDirectories();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Nexus.events().post(TickEvent.INSTANCE);
            Nexus.config().tick();
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.STATUS_EFFECTS, Identifier.of(MOD_ID, "hud"),
                (context, tickCounter) -> Nexus.hud().render(context, tickCounter));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            Nexus.session().onJoin();
            Nexus.events().post(WorldJoinEvent.INSTANCE);
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            Nexus.session().onLeave();
            Nexus.events().post(WorldLeaveEvent.INSTANCE);
            Nexus.config().flushAsync();
        }));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) Nexus.events().post(new AttackEntityEvent(entity));
            return ActionResult.PASS;
        });

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> Nexus.config().loadInitial());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Nexus.config().saveBlocking();
            Nexus.modules().shutdown();
        });

        Nexus.events().subscribe(KeyEvent.class, e -> {
            if (e.isPress() && Nexus.settings().menuKey.matchesKey(e.key())) openMenu();
        });
        Nexus.events().subscribe(MouseButtonEvent.class, e -> {
            if (e.isPress() && Nexus.settings().menuKey.matchesMouse(e.button())) openMenu();
        });

        LOGGER.info("{} {} initialised with {} modules in {} ms", Nexus.NAME, Nexus.VERSION,
                Nexus.modules().all().size(), (System.nanoTime() - start) / 1_000_000);
    }

    private static void openMenu() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.player == null) return;
        if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_F3)) return;
        mc.setScreen(new NexusScreen());
    }

    /** New modules only need to be added here. */
    private static void registerModules(ModuleManager m) {
        // Visuals
        m.register(new FullbrightModule());
        m.register(new CustomCrosshairModule());
        m.register(new CustomNametagsModule());
        // HUD
        m.register(new ArmorHudModule());
        m.register(new PotionHudModule());
        m.register(new CoordinatesHudModule());
        m.register(new FpsHudModule());
        m.register(new KeystrokesModule());
        m.register(new TargetHudModule());
        m.register(new WorldTimeHudModule());
        m.register(new ModuleListHudModule());
        // Interface
        m.register(new NotificationsModule());
        // Utility
        m.register(new ZoomModule());
        m.register(new AutoSprintModule());
        // Performance
        m.register(new UnfocusedFpsModule());
    }
}
