package dev.nexus.client.ui.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.nexus.client.ClientSettings;
import dev.nexus.client.Nexus;
import dev.nexus.client.NexusClient;
import dev.nexus.client.ui.animation.AnimationClock;
import dev.nexus.client.ui.animation.AnimationSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Animated photo background of the out-of-game menus (replaces the vanilla panorama).
 * <p>
 * Photos come from {@code config/nexus/backgrounds/} (PNG/JPG, user supplied) or, when that folder holds no images,
 * from the photos bundled in the mod ({@code assets/nexus/backgrounds/}). They are shown as a slideshow: each photo
 * slowly zooms and pans (Ken Burns effect), consecutive photos cross-fade, and the whole picture follows the mouse
 * slightly (parallax). Timing comes from the central {@link AnimationClock}.
 * <p>
 * Memory: at most two photos are decoded at a time (the visible one and the next one). Decoding runs on a worker
 * thread and downsizes the image to roughly the window resolution; only the GPU upload happens on the render thread.
 * The per-frame drawing path allocates nothing.
 */
public final class MenuBackground {
    private static final MenuBackground INSTANCE = new MenuBackground();
    private static final String FOLDER = "backgrounds";
    private static final int MAX_PHOTOS = 64;
    private static final long MAX_FILE_BYTES = 48L * 1024 * 1024;
    private static final long FADE_MS = 1600L;
    private static final long FIRST_FADE_MS = 700L;
    /** Ken Burns zoom range (1.0 = photo exactly covers the screen). */
    private static final float ZOOM = 0.14F;
    /** Parallax amplitude as a fraction of the screen size. */
    private static final float PARALLAX = 0.018F;
    private static final int BACKDROP = 0xFF07070B;

    private final List<Source> sources = new ArrayList<>();
    private String signature = "";
    private int textureCounter;
    private int sequence;
    @Nullable private Slide current;
    @Nullable private Slide next;
    private boolean fading;
    private int failedInRow;
    // smoothed parallax
    private float parX;
    private float parY;
    private long lastFrameMs;

    private MenuBackground() {}

    public static MenuBackground get() {
        return INSTANCE;
    }

    /** Folder for user photos, created on demand. */
    public static Path folder() {
        Path dir = Nexus.config().root().resolve(FOLDER);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            NexusClient.LOGGER.warn("Cannot create {}: {}", dir, e.toString());
        }
        return dir;
    }

    private static ClientSettings settings() {
        return Nexus.settings();
    }

    /** True when photos are enabled and at least one photo exists. */
    public boolean active() {
        return settings().menuBackground.is("Photos") && !this.sources.isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ sources

    /**
     * Re-scans the photo folder and the bundled photos. Called when the title screen initialises (not per frame);
     * if nothing changed this only lists the folder.
     */
    public void refresh(MinecraftClient client) {
        Map<String, Source> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        StringBuilder sig = new StringBuilder();
        Path dir = folder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!isImage(name) || !Files.isRegularFile(p)) continue;
                found.put(name, new Source(name, p, null));
            }
        } catch (IOException e) {
            NexusClient.LOGGER.warn("Cannot list {}: {}", dir, e.toString());
        }
        if (found.isEmpty()) {
            try {
                Map<Identifier, Resource> res = client.getResourceManager().findResources(FOLDER,
                        id -> "nexus".equals(id.getNamespace()) && isImage(id.getPath()));
                for (Identifier id : res.keySet()) found.put(id.toString(), new Source(id.toString(), null, id));
            } catch (RuntimeException e) {
                NexusClient.LOGGER.warn("Cannot list bundled backgrounds: {}", e.toString());
            }
        }
        List<Source> list = new ArrayList<>(found.values());
        if (list.size() > MAX_PHOTOS) list = list.subList(0, MAX_PHOTOS);
        for (Source s : list) {
            sig.append(s.name).append('|');
            if (s.file != null) {
                try {
                    sig.append(Files.size(s.file)).append('@').append(Files.getLastModifiedTime(s.file).toMillis());
                } catch (IOException ignored) {
                    sig.append('?');
                }
            }
            sig.append(';');
        }
        String newSig = sig.toString();
        if (newSig.equals(this.signature)) return;
        releaseAll();
        this.signature = newSig;
        this.sources.clear();
        this.sources.addAll(list);
        this.failedInRow = 0;
        if (!list.isEmpty()) NexusClient.LOGGER.info("Menu background: {} photo(s) from {}", list.size(),
                list.get(0).file != null ? dir : "the mod");
    }

    private static boolean isImage(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /** Frees all photo textures (e.g. when a world is joined); they are reloaded when a menu needs them again. */
    public void releaseAll() {
        release(this.current);
        release(this.next);
        this.current = null;
        this.next = null;
        this.fading = false;
        for (int i = 0; i < this.cardSlides.length; i++) {
            release(this.cardSlides[i]);
            this.cardSlides[i] = null;
        }
    }

    // ------------------------------------------------------------------------------------------------ sky cards
    /** Extra persistent photo slots used by the Sky-style title cards (decoded by the same pipeline). */
    private final Slide[] cardSlides = new Slide[3];
    private static final int[] CARD_INDEX = {1, 5, 7};

    /** Returns the texture for card slot {@code i}, or null while it is still decoding. */
    @Nullable
    public Identifier cardPhoto(int i, int[] dims) {
        i = ((i % this.cardSlides.length) + this.cardSlides.length) % this.cardSlides.length;
        if (this.sources.size() < 2) return null;
        Slide s = this.cardSlides[i];
        if (s == null) {
            int idx = Math.min(Math.min(i + 1, CARD_INDEX[i]), this.sources.size() - 1);
            s = request(idx);
            this.cardSlides[i] = s;
        }
        if (!pump(s) || s.texture == null) return null;
        if (dims != null && dims.length >= 2) {
            dims[0] = s.width;
            dims[1] = s.height;
        }
        return s.id;
    }

    private void release(@Nullable Slide s) {
        if (s == null) return;
        if (s.texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(s.id);
            s.texture = null;
        }
        if (s.pending != null) {
            // image decoded after release: free it
            s.pending.thenAccept(img -> {
                if (img != null) img.close();
            });
            s.pending = null;
        }
    }

    // ------------------------------------------------------------------------------------------------ loading

    private Slide request(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        Source src = this.sources.get(index);
        Slide slide = new Slide(index, this.sequence++);
        slide.id = Identifier.of("nexus", "dynamic/menu_bg_" + this.textureCounter++);
        // decode at about the framebuffer size, with headroom for zoom and parallax
        float head = 1.0F + ZOOM + PARALLAX * 2.0F + 0.02F;
        int tw = Math.max(64, Math.round(client.getWindow().getFramebufferWidth() * head));
        int th = Math.max(64, Math.round(client.getWindow().getFramebufferHeight() * head));
        slide.pending = CompletableFuture.supplyAsync(() -> {
            try {
                return decode(src.read(client), tw, th);
            } catch (IOException | RuntimeException e) {
                NexusClient.LOGGER.warn("Cannot load background {}: {}", src.name, e.toString());
                return null;
            }
        }, Util.getIoWorkerExecutor());
        return slide;
    }

    /** Decodes PNG/JPEG (stb_image) and shrinks it so it just covers {@code tw x th}. Runs on a worker thread. */
    /** Decodes PNG/JPEG (stb_image) and shrinks it so it just covers {@code tw x th}. Runs on a worker thread. */
    private static NativeImage decode(byte[] data, int tw, int th) throws IOException {
        ByteBuffer buf = MemoryUtil.memAlloc(data.length);
        NativeImage src;
        try {
            buf.put(data).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer c = stack.mallocInt(1);
                ByteBuffer px = STBImage.stbi_load_from_memory(buf, w, h, c, 4);
                if (px == null) throw new IOException("unsupported image: " + STBImage.stbi_failure_reason());
                src = new NativeImage(NativeImage.Format.RGBA, w.get(0), h.get(0), true, MemoryUtil.memAddress(px));
            }
        } finally {
            MemoryUtil.memFree(buf);
        }
        int iw = src.getWidth();
        int ih = src.getHeight();
        // a heavily blurred photo looks creamy behind the menu and decodes/draws faster
        boolean blur = settings().backgroundBlur.isOn();
        float scale = Math.max(tw / (float) iw, th / (float) ih) * (blur ? 0.34F : 1.0F);
        NativeImage dst = src;
        if (scale < 1.0F) {
            int nw = Math.max(1, Math.round(iw * scale));
            int nh = Math.max(1, Math.round(ih * scale));
            dst = new NativeImage(NativeImage.Format.RGBA, nw, nh, false);
            try {
                src.resizeSubRectTo(0, 0, iw, ih, dst);
            } catch (RuntimeException e) {
                dst.close();
                src.close();
                throw e;
            }
            src.close();
        }
        if (blur) {
            boxBlur(dst, 8);
            boxBlur(dst, 8);
        }
        return dst;
    }

    /** Two-pass separable box blur (horizontal + vertical), operating on ARGB pixels. */
    private static void boxBlur(NativeImage img, int r) {
        int w = img.getWidth();
        int h = img.getHeight();
        int win = 2 * r + 1;
        int[] tmp = new int[w * h];
        for (int y = 0; y < h; y++) {
            long as = 0, rs = 0, gs = 0, bs = 0;
            for (int x = -r; x <= r; x++) {
                int c = img.getColorArgb(Math.max(0, Math.min(w - 1, x)), y);
                as += c >>> 24; rs += (c >> 16) & 0xFF; gs += (c >> 8) & 0xFF; bs += c & 0xFF;
            }
            int row = y * w;
            for (int x = 0; x < w; x++) {
                tmp[row + x] = (int) (as / win) << 24 | (int) (rs / win) << 16 | (int) (gs / win) << 8 | (int) (bs / win);
                int co = img.getColorArgb(Math.max(0, x - r), y);
                int ci = img.getColorArgb(Math.min(w - 1, x + r + 1), y);
                as += (ci >>> 24) - (co >>> 24);
                rs += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                gs += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                bs += (ci & 0xFF) - (co & 0xFF);
            }
        }
        for (int x = 0; x < w; x++) {
            long as = 0, rs = 0, gs = 0, bs = 0;
            for (int y = -r; y <= r; y++) {
                int c = tmp[Math.max(0, Math.min(h - 1, y)) * w + x];
                as += c >>> 24; rs += (c >> 16) & 0xFF; gs += (c >> 8) & 0xFF; bs += c & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                img.setColorArgb(x, y, (int) (as / win) << 24 | (int) (rs / win) << 16 | (int) (gs / win) << 8 | (int) (bs / win));
                int co = tmp[Math.max(0, y - r) * w + x];
                int ci = tmp[Math.min(h - 1, y + r + 1) * w + x];
                as += (ci >>> 24) - (co >>> 24);
                rs += ((ci >> 16) & 0xFF) - ((co >> 16) & 0xFF);
                gs += ((ci >> 8) & 0xFF) - ((co >> 8) & 0xFF);
                bs += (ci & 0xFF) - (co & 0xFF);
            }
        }
    }

    private boolean pump(Slide s) {
        if (s.texture != null || s.pending == null || !s.pending.isDone()) return true;
        NativeImage img = s.pending.getNow(null);
        s.pending = null;
        if (img == null) return false;
        PhotoTexture tex = new PhotoTexture(s.id.toString(), img);
        MinecraftClient.getInstance().getTextureManager().registerTexture(s.id, tex);
        s.texture = tex;
        s.width = img.getWidth();
        s.height = img.getHeight();
        return true;
    }

    private int nextIndex(int index) {
        int n = this.sources.size();
        if (n <= 1) return 0;
        if (settings().photoShuffle.get()) {
            int r = ThreadLocalRandom.current().nextInt(n - 1);
            return r >= index ? r + 1 : r;
        }
        return (index + 1) % n;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    /**
     * Draws the photo background over the whole screen. Returns false (nothing drawn) when photos are disabled or
     * none are available, so the caller can fall back to the vanilla panorama.
     */
    public boolean render(DrawContext ctx, int w, int h) {
        if (!active()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        long now = AnimationClock.millis();
        long frameDt = this.lastFrameMs == 0L ? 0L : Math.min(100L, Math.max(0L, now - this.lastFrameMs));
        this.lastFrameMs = now;
        ctx.fill(0, 0, w, h, BACKDROP);

        if (this.current == null) {
            this.current = request(settings().photoShuffle.get() ? ThreadLocalRandom.current().nextInt(this.sources.size()) : 0);
            this.current.first = true;
            this.fading = false;
        }
        if (!pump(this.current)) {
            // broken file: skip it
            int idx = this.current.index;
            release(this.current);
            this.current = null;
            if (++this.failedInRow >= this.sources.size()) {
                this.sources.clear();
                return false;
            }
            this.current = request(nextIndex(idx));
            this.current.first = true;
            return true;
        }
        if (this.current.texture == null) return true; // still decoding
        this.failedInRow = 0;
        if (this.current.start == 0L) this.current.start = now;

        boolean instant = AnimationSettings.instant();
        long hold = Math.round(settings().slideDuration.get() * 1000.0);
        if (this.sources.size() > 1) {
            if (this.next == null) this.next = request(nextIndex(this.current.index));
            if (!pump(this.next)) {
                int idx = this.next.index;
                release(this.next);
                this.next = request(nextIndex(idx));
            } else if (!this.fading && this.next.texture != null && now - this.current.start >= hold) {
                this.fading = true;
                this.next.start = now;
            }
            if (this.fading && (instant || now - this.next.start >= FADE_MS)) {
                release(this.current);
                this.current = this.next;
                this.next = null;
                this.fading = false;
            }
        }

        // parallax follows the mouse, smoothed in time
        float tx = 0.0F;
        float ty = 0.0F;
        if (settings().photoParallax.get() && client.getWindow().getWidth() > 0 && client.getWindow().getHeight() > 0) {
            tx = (float) (client.mouse.getX() / client.getWindow().getWidth()) * 2.0F - 1.0F;
            ty = (float) (client.mouse.getY() / client.getWindow().getHeight()) * 2.0F - 1.0F;
            tx = Math.max(-1.0F, Math.min(1.0F, tx));
            ty = Math.max(-1.0F, Math.min(1.0F, ty));
        }
        if (instant) {
            this.parX = tx;
            this.parY = ty;
        } else {
            float k = 1.0F - (float) Math.exp(-frameDt / 260.0);
            this.parX += (tx - this.parX) * k;
            this.parY += (ty - this.parY) * k;
        }

        long cycle = hold + FADE_MS;
        Slide cur = this.current;
        float curAlpha = instant ? 1.0F : Math.min(1.0F, (now - cur.start) / (float) FIRST_FADE_MS);
        if (!cur.first) curAlpha = 1.0F; // only the first photo fades in from the backdrop
        draw(ctx, cur, w, h, now, cycle, curAlpha, instant);
        if (this.fading && this.next != null && this.next.texture != null) {
            float t = Math.min(1.0F, (now - this.next.start) / (float) FADE_MS);
            float a = t * t * (3.0F - 2.0F * t);
            draw(ctx, this.next, w, h, now, cycle, a, instant);
        }
        return true;
    }

    private void draw(DrawContext ctx, Slide s, int w, int h, long now, long cycle, float alpha, boolean instant) {
        if (alpha <= 0.0F || s.width <= 0) return;
        boolean motion = settings().photoMotion.get() && !instant;
        // motion progress 0..1, ping-pong if the photo stays longer than one cycle
        float p = 0.0F;
        if (motion) {
            long el = Math.max(0L, now - s.start) % (cycle * 2L);
            p = el < cycle ? el / (float) cycle : 2.0F - el / (float) cycle;
            p = p * p * (3.0F - 2.0F * p);
        }
        // per-photo variation from its sequence number (deterministic, no allocation)
        float r1 = hash(s.sequence * 2 + 1);
        boolean zoomIn = (s.sequence & 1) == 0;
        float zoom = motion ? 1.0F + ZOOM * (zoomIn ? p : 1.0F - p) : 1.0F;
        double angle = r1 * Math.PI * 2.0;
        float dirX = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);
        float cover = Math.max(w / (float) s.width, h / (float) s.height);
        float par = settings().photoParallax.get() ? PARALLAX : 0.0F;
        float scale = cover * zoom * (1.0F + par * 2.0F);
        float drawnW = s.width * scale;
        float drawnH = s.height * scale;
        // pan inside the margin created by the zoom (the parallax margin is reserved separately)
        float marginX = (s.width * cover * zoom - w) / 2.0F;
        float marginY = (s.height * cover * zoom - h) / 2.0F;
        float f = motion ? (p * 2.0F - 1.0F) * 0.9F : 0.0F;
        float ox = dirX * f * Math.max(0.0F, marginX) - this.parX * par * w;
        float oy = dirY * f * Math.max(0.0F, marginY) - this.parY * par * h;
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(w / 2.0F + ox - drawnW / 2.0F, h / 2.0F + oy - drawnH / 2.0F);
        m.scale(scale, scale);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, s.id, 0, 0, 0.0F, 0.0F, s.width, s.height, s.width, s.height,
                s.width, s.height, (a << 24) | 0xFFFFFF);
        m.popMatrix();
    }

    private static float hash(int n) {
        int x = n * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA77;
        x ^= x >>> 13;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    // ------------------------------------------------------------------------------------------------ types

    private record Source(String name, @Nullable Path file, @Nullable Identifier resource) {
        byte[] read(MinecraftClient client) throws IOException {
            if (this.file != null) {
                long size = Files.size(this.file);
                if (size > MAX_FILE_BYTES) throw new IOException("file is larger than 48 MB");
                return Files.readAllBytes(this.file);
            }
            Resource res = client.getResourceManager().getResource(this.resource)
                    .orElseThrow(() -> new IOException("missing resource " + this.resource));
            try (InputStream in = res.getInputStream()) {
                return in.readAllBytes();
            }
        }
    }

    private static final class Slide {
        final int index;
        final int sequence;
        Identifier id;
        @Nullable CompletableFuture<NativeImage> pending;
        @Nullable PhotoTexture texture;
        int width;
        int height;
        long start;
        boolean first;

        Slide(int index, int sequence) {
            this.index = index;
            this.sequence = sequence;
        }
    }

    /** Dynamic texture with linear filtering, so slow zoom/pan looks smooth instead of shimmering. */
    private static final class PhotoTexture extends NativeImageBackedTexture {
        PhotoTexture(String name, NativeImage image) {
            super(() -> name, image);
            this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        }
    }
}
