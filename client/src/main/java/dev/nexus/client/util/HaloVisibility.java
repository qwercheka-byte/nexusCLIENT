package dev.nexus.client.util;

import dev.nexus.client.Nexus;
import dev.nexus.client.module.impl.hud.HaloHudModule;

/** Shared check: Halo HUD active and configured to replace the vanilla survival layers. */
public final class HaloVisibility {
    private HaloVisibility() { }

    public static boolean hidesVanilla() {
        if (Nexus.modules() == null) return false;
        HaloHudModule halo = Nexus.modules().get(HaloHudModule.class);
        return halo != null && halo.isEnabled() && halo.hidesVanilla();
    }
}
