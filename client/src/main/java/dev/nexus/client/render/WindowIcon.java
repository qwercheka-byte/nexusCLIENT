package dev.nexus.client.render;

import dev.nexus.client.NexusClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.InputSupplier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * NEXUS icon of the game window (title bar, taskbar, Alt+Tab) instead of the Minecraft one.
 *
 * <p>Vanilla creates the window visible and sets its icon a little later. On Windows the taskbar button often keeps
 * the icon of javaw.exe (the Java "Duke") in that case, so on Windows the window is created hidden, gets the icon and
 * is shown only then (see WindowMixin). Vanilla's own later {@code setIcon} call receives the same icons through
 * IconsMixin, so it does not put the Minecraft icon back.
 */
public final class WindowIcon {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    private static final String PATH = "/assets/nexus/textures/window/icon_%d.png";

    private WindowIcon() {
    }

    /** Platforms where GLFW can set a window icon (Wayland and macOS cannot). */
    public static boolean supported() {
        int platform = GLFW.glfwGetPlatform();
        return platform == GLFW.GLFW_PLATFORM_WIN32 || platform == GLFW.GLFW_PLATFORM_X11;
    }

    /** Only Windows needs the "create hidden, set icon, show" order for the taskbar button. */
    public static boolean showAfterIcon() {
        return GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WIN32;
    }

    /** Icon images in the format vanilla's {@code Window.setIcon} expects. */
    public static List<InputSupplier<InputStream>> icons() {
        List<InputSupplier<InputStream>> list = new ArrayList<>(SIZES.length);
        for (int size : SIZES) {
            String path = PATH.formatted(size);
            list.add(() -> {
                InputStream in = WindowIcon.class.getResourceAsStream(path);
                if (in == null) throw new IOException("Missing " + path);
                return in;
            });
        }
        return list;
    }

    /** Sets the NEXUS icon on a GLFW window; failures are only logged (the window keeps its previous icon). */
    public static void apply(long window) {
        if (window == 0L || !supported()) return;
        List<InputSupplier<InputStream>> icons = icons();
        List<ByteBuffer> pixels = new ArrayList<>(icons.size());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWImage.Buffer images = GLFWImage.malloc(icons.size(), stack);
            for (int i = 0; i < icons.size(); i++) {
                try (InputStream in = icons.get(i).get(); NativeImage image = NativeImage.read(in)) {
                    ByteBuffer buffer = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                    pixels.add(buffer);
                    buffer.asIntBuffer().put(image.copyPixelsAbgr());
                    images.position(i);
                    images.width(image.getWidth());
                    images.height(image.getHeight());
                    images.pixels(buffer);
                }
            }
            images.position(0);
            GLFW.glfwSetWindowIcon(window, images);
        } catch (Exception e) {
            NexusClient.LOGGER.warn("Cannot set the NEXUS window icon: {}", e.toString());
        } finally {
            pixels.forEach(MemoryUtil::memFree);
        }
    }
}
