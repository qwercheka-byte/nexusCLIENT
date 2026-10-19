package dev.nexus.launcher.ui;

import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Tasks;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Player heads cut out of the skin texture (face + hat layer), cached per skin URL. */
final class Heads {
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private Heads() {}

    /** Head of a Microsoft account with a skin, otherwise a letter tile. */
    static Node head(Account a, double size) {
        StackPane holder = new StackPane(Fx.tile(a.name, a.isMicrosoft() ? "violet" : "slate", size));
        holder.setMinSize(size, size);
        holder.setMaxSize(size, size);
        if (a.skinUrl == null || a.skinUrl.isBlank()) return holder;
        Image cached = CACHE.get(a.skinUrl);
        if (cached != null) {
            holder.getChildren().setAll(view(cached, size));
            return holder;
        }
        String url = a.skinUrl.replace("http://", "https://");
        Tasks.io().execute(() -> {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", BuildInfo.userAgent()).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) return;
                Image skin = new Image(new ByteArrayInputStream(r.body()));
                if (skin.isError() || skin.getWidth() < 64) return;
                Image face = crop(skin);
                CACHE.put(a.skinUrl, face);
                Platform.runLater(() -> holder.getChildren().setAll(view(face, size)));
            } catch (Exception e) {
                Log.warn("Cannot load skin of " + a.name + ": " + e.getMessage());
            }
        });
        return holder;
    }

    private static Node view(Image face, double size) {
        ImageView v = new ImageView(face);
        v.setFitWidth(size);
        v.setFitHeight(size);
        v.setSmooth(false);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size * 0.42);
        clip.setArcHeight(size * 0.42);
        v.setClip(clip);
        return v;
    }

    /** 8×8 face at (8,8) with the hat overlay at (40,8), upscaled ×8 without smoothing. */
    private static Image crop(Image skin) {
        int scale = (int) (skin.getWidth() / 64);
        PixelReader r = skin.getPixelReader();
        int out = 64;
        WritableImage img = new WritableImage(out, out);
        for (int y = 0; y < out; y++) {
            for (int x = 0; x < out; x++) {
                int sx = x * 8 * scale / out, sy = y * 8 * scale / out;
                int base = r.getArgb(8 * scale + sx, 8 * scale + sy);
                int hat = r.getArgb(40 * scale + sx, 8 * scale + sy);
                int argb = (hat >>> 24) > 0x80 ? hat : base | 0xff000000;
                img.getPixelWriter().setArgb(x, y, argb);
            }
        }
        return img;
    }
}
