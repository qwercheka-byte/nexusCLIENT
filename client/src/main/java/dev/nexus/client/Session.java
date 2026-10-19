package dev.nexus.client;

import dev.nexus.client.util.Mc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;

/** Information about the current play session (shown on the Home page). */
public final class Session {
    private long joinedAtMs = -1;
    private final long launchedAtMs = System.currentTimeMillis();

    void onJoin() {
        joinedAtMs = System.currentTimeMillis();
    }

    void onLeave() {
        joinedAtMs = -1;
    }

    public boolean inSession() {
        return joinedAtMs >= 0 && Mc.inWorld();
    }

    public long sessionSeconds() {
        return joinedAtMs < 0 ? 0 : (System.currentTimeMillis() - joinedAtMs) / 1000;
    }

    public long uptimeSeconds() {
        return (System.currentTimeMillis() - launchedAtMs) / 1000;
    }

    public String serverName() {
        MinecraftClient mc = Mc.get();
        if (!Mc.inWorld()) return "Not connected";
        if (mc.isInSingleplayer()) return "Singleplayer";
        ServerInfo info = mc.getCurrentServerEntry();
        return info != null ? info.address : "Multiplayer";
    }

    /** Ping in ms, or -1 when unknown / singleplayer. */
    public int ping() {
        MinecraftClient mc = Mc.get();
        if (mc.player == null || mc.getNetworkHandler() == null || mc.isInSingleplayer()) return -1;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? -1 : e.getLatency();
    }

    public String playerName() {
        return Mc.get().getSession().getUsername();
    }
}
