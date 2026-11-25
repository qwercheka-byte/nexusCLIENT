package dev.nexus.client.render;
import net.minecraft.class_332;
public class Render2D {
    public static int count; public static float x, y;
    public static void roundedOutline(class_332 c, float x, float y, float w, float h, float r, float t, int color) {
        if (count++ == 0) { Render2D.x=x; Render2D.y=y; }
    }
}
