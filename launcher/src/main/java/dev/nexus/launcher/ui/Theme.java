package dev.nexus.launcher.ui;

import javafx.scene.Parent;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.Map;

/** Accent palettes. Applied by overriding the looked-up colours on the scene root. */
public final class Theme {
    /** accent id → {primary, secondary}. Keys match {@code Settings.ACCENTS}. */
    public static final Map<String, String[]> ACCENTS = new LinkedHashMap<>();

    static {
        ACCENTS.put("violet", new String[]{"#8b5cf6", "#38bdf8"});
        ACCENTS.put("cyan", new String[]{"#22b8e6", "#a78bfa"});
        ACCENTS.put("emerald", new String[]{"#10b981", "#38bdf8"});
        ACCENTS.put("rose", new String[]{"#f43f5e", "#f59e0b"});
        ACCENTS.put("amber", new String[]{"#f59e0b", "#f43f5e"});
    }

    private Theme() {}

    public static void apply(Parent root, String accent) {
        String[] c = ACCENTS.getOrDefault(accent, ACCENTS.get("violet"));
        Color a = Color.web(c[0]);
        String soft = String.format("rgba(%d,%d,%d,0.16)", (int) (a.getRed() * 255), (int) (a.getGreen() * 255), (int) (a.getBlue() * 255));
        root.setStyle("-nx-accent: " + c[0] + "; -nx-accent-2: " + c[1] + "; -nx-accent-soft: " + soft + ";");
    }

    public static String primary(String accent) {
        return ACCENTS.getOrDefault(accent, ACCENTS.get("violet"))[0];
    }
}
