package dev.nexus.launcher.ui;

import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.auth.AccountStore;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sky-DLC-style home page: full-photo background, centered greeting, three large cards
 * (last played instance / all instances / accounts) and a small bottom button bar.
 * Used instead of the instance list as the start page; launched games go through card #1.
 */
public final class HomePage extends VBox implements MainWindow.Page {
    private final MainWindow window;
    private final Label greeting = new Label();
    private final Label subLast = new Label();
    private final Label subAll = new Label();
    private final Label subAcc = new Label();
    private final Label capLast = new Label();
    private final Timeline slideshow;

    public HomePage(MainWindow window) {
        this.window = window;
        getStyleClass().add("home-page");
        setFillWidth(true);
        setAlignment(Pos.TOP_CENTER);
        setSpacing(10);

        Label hello = greeting;
        hello.getStyleClass().addAll("home-greeting");
        hello.setStyle("-fx-font-size: 34px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label hint = Fx.label(I18n.t("home.hint"), "home-hint");
        hint.setStyle("-fx-text-fill: -nx-text-dim;");

        HBox cards = new HBox(24,
                cardLast(),
                cardPhoto(I18n.t("home.card.all"), subAll, null, this::goInstances, false),
                cardPhoto(I18n.t("home.card.accounts"), subAcc, null, window::showAccounts, false));
        cards.setAlignment(Pos.CENTER);

        String[] btns = {I18n.t("home.btn.settings"), I18n.t("home.btn.mods"), I18n.t("home.btn.instances")};
        Runnable[] acts = {window::showSettings, () -> {
            Instance m = lastPlayed();
            if (m != null) window.showInstance(m, "mods");
        }, this::goInstances};
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER);
        for (int i = 0; i < btns.length; i++) {
            Button b = Fx.button(btns[i], null, "home-bar-button");
            b.setStyle("-fx-background-color: rgba(20,22,30,0.75); -fx-text-fill: -nx-text-dim; -fx-background-radius: 10; -fx-padding: 9 18 9 18;");
            int j = i;
            if (j == 1) b.setDisable(lastPlayed() == null);
            b.setOnAction(e -> acts[j].run());
            bar.getChildren().add(b);
        }
        Button quit = Fx.button(I18n.t("home.btn.quit"), null, "home-bar-button");
        quit.setStyle("-fx-background-color: rgba(20,22,30,0.75); -fx-text-fill: -nx-danger; -fx-background-radius: 10; -fx-padding: 9 18 9 18;");
        quit.setOnAction(e -> window.requestExit());
        bar.getChildren().add(quit);

        Region glue0 = grow(), glue1 = grow(), glue2 = grow(), glue3 = grow();
        getChildren().addAll(brandRow(), glue0, center(hello), center(hint), glue1, cards, glue2, bar, glue3);

        slideshow = new Timeline(new KeyFrame(Duration.seconds(20), e -> rotatePhotos()));
        slideshow.setCycleCount(Timeline.INDEFINITE);
    }

    private Region grow() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    private StackPane center(Label l) {
        StackPane s = new StackPane(l);
        return s;
    }

    private HBox brandRow() {
        Label brand = Fx.label("➤ NEXUS", "home-brand");
        brand.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 17px; -fx-letter-spacing: 1.5px;");
        HBox row = new HBox(brand);
        row.setPadding(new Insets(24, 0, 0, 34));
        return row;
    }

    // ------------------------------------------------------------- cards

    private List<File> photos() {
        List<File> out = new ArrayList<>();
        Path user = PhotoBackground.folder();
        if (user != null && Files.isDirectory(user)) {
            try (var s = Files.list(user)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().matches(".*\\.(png|jpe?g|webp)"))
                        .forEach(p -> out.add(p.toFile()));
            } catch (Exception e) {
                Log.warn("home: cannot scan backgrounds", e);
            }
        }
        if (out.isEmpty()) {
            for (int i = 1; i <= 8; i++) {
                var u = getClass().getResource("/dev/nexus/launcher/backgrounds/bg_" + i + ".jpg");
                if (u != null) return List.of(); // resource photos are handled by cardPhoto via resource lookup
            }
        }
        out.sort(Comparator.comparing(File::getName));
        return out;
    }

    private String pickPhoto(int slot) {
        List<File> f = photos();
        if (!f.isEmpty()) return "file:" + f.get(slot % f.size()).getAbsolutePath();
        int i = 1 + ((slot % 10) + 10) % 10;
        var u = getClass().getResource(String.format("/dev/nexus/launcher/backgrounds/bg_%02d.jpg", i));
        return u == null ? null : u.toExternalForm();
    }

    private final List<ImageView> photoViews = new ArrayList<>();

    private StackPane cardPhoto(String caption, Label sub, String photoUrl, Runnable onClick, boolean play) {
        Label cap = new Label(caption);
        cap.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        cap.setWrapText(true);
        sub.setStyle("-fx-text-fill: -nx-text-dim; -fx-font-size: 12.5px;");
        sub.setWrapText(true);

        ImageView img = new ImageView();
        img.setFitWidth(248);
        img.setFitHeight(320);
        img.setPreserveRatio(false);
        if (photoUrl != null) img.setImage(new Image(photoUrl, true));
        photoViews.add(img);
        Rectangle clip = new Rectangle(248, 320);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        img.setClip(clip);
        ColorAdjust dark = new ColorAdjust();
        dark.setBrightness(-0.35);
        img.setEffect(dark);

        VBox bottom = new VBox(3, cap, sub);
        bottom.setPadding(new Insets(0, 0, 2, 0));
        bottom.setAlignment(Pos.BOTTOM_LEFT);

        StackPane overlayBottom = new StackPane(bottom);
        StackPane.setAlignment(bottom, Pos.BOTTOM_LEFT);

        StackPane card = new StackPane(img, overlayBottom);
        card.setMinSize(248, 320);
        card.setMaxSize(248, 320);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: rgba(13,14,19,0.42); -fx-background-radius: 22; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 22, 0, 0, 8);");
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> onClick.run());
        DropShadow glow = new DropShadow(34, Color.web("#7c6cff66"));
        card.setOnMouseEntered(e -> card.setEffect(glow));
        card.setOnMouseExited(e -> card.setEffect(null));
        return card;
    }

    private StackPane cardLast() {
        Instance last = lastPlayed();
        capLast.setText(last == null ? I18n.t("home.card.last.empty") : I18n.t("home.card.last"));
        capLast.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        subLast.setStyle("-fx-text-fill: -nx-text-dim; -fx-font-size: 12.5px;");
        subLast.setWrapText(true);

        ImageView img = new ImageView();
        img.setFitWidth(248);
        img.setFitHeight(320);
        img.setPreserveRatio(false);
        String url = pickPhoto(1);
        if (url != null) img.setImage(new Image(url, true));
        photoViews.add(img);
        Rectangle clip = new Rectangle(248, 320);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        img.setClip(clip);
        ColorAdjust dark = new ColorAdjust();
        dark.setBrightness(-0.35);
        img.setEffect(dark);

        Button playBtn = new Button(I18n.t("home.play"));
        playBtn.setDefaultButton(true);
        playBtn.setStyle("-fx-background-color: white; -fx-text-fill: #14151d; -fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 9 0 9 0; -fx-cursor: hand;");
        playBtn.setMaxWidth(Double.MAX_VALUE);
        playBtn.setDisable(last == null);
        playBtn.setOnAction(e -> playLast());

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        VBox bottom = new VBox(8, sp, new VBox(3, capLast, subLast), playBtn);
        bottom.setFillWidth(true);
        StackPane.setAlignment(bottom, Pos.BOTTOM_LEFT);

        StackPane card = new StackPane(img, bottom);
        card.setMinSize(248, 320);
        card.setMaxSize(248, 320);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: rgba(13,14,19,0.42); -fx-background-radius: 22; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 22, 0, 0, 8);");
        DropShadow glow = new DropShadow(34, Color.web("#7c6cff66"));
        card.setOnMouseEntered(e -> card.setEffect(glow));
        card.setOnMouseExited(e -> card.setEffect(null));
        return card;
    }

    private Instance lastPlayed() {
        List<Instance> all = InstanceManager.all();
        return all.stream().filter(i -> i.lastPlayed > 0).max(Comparator.comparingLong(i -> i.lastPlayed)).orElse(null);
    }

    private String ago(long ms) {
        long m = Math.max(1, (System.currentTimeMillis() - ms) / 60000);
        if (m < 60) return I18n.t("home.ago.min", m);
        long h = m / 60;
        if (h < 24) return I18n.t("home.ago.hours", h);
        return I18n.t("home.ago.days", h / 24);
    }

    private void playLast() {
        Instance last = lastPlayed();
        if (last != null) LaunchController.launch(last);
    }

    private void goInstances() {
        window.showInstances();
    }

    private void rotatePhotos() {
        List<File> f = photos();
        if (f.size() < 2) return;
        for (int i = 0; i < photoViews.size(); i++) {
            String url = pickPhoto(i + (int) (System.currentTimeMillis() / 20000));
            if (url != null) photoViews.get(i).setImage(new Image(url, true));
        }
    }

    public void onShow() {
        Account acc = AccountStore.active();
        String name = acc != null && acc.name != null ? acc.name : I18n.t("home.player");
        greeting.setText(I18n.t("home.greeting", name));
        Instance last = lastPlayed();
        if (last != null) {
            capLast.setText(last.name.isBlank() ? I18n.t("home.card.last") : last.name);
            String sub = last.mcVersion == null ? "" : last.mcVersion;
            String hours = last.playtimeSeconds > 1800 ? I18n.t("home.playHours", Math.round(last.playtimeSeconds / 360.0) / 10.0) : "";
            subLast.setText((sub.isBlank() ? "" : sub + " · ") + I18n.t("home.ago", ago(last.lastPlayed)) + (hours.isEmpty() ? "" : " · " + hours));
        } else {
            capLast.setText(I18n.t("home.card.last.empty"));
            subLast.setText(I18n.t("home.card.last.emptyHint"));
        }
        int n = InstanceManager.all().size();
        subAll.setText(n == 0 ? I18n.t("home.card.all.empty") : I18n.t("home.card.all.count", n));
        int a = AccountStore.all().size();
        subAcc.setText(a == 0 ? I18n.t("home.card.accounts.empty") : I18n.t("home.card.accounts.count", a));
        slideshow.playFromStart();
        applyBackgroundPhotos();
    }

    public void onHide() {
        slideshow.stop();
    }

    private void applyBackgroundPhotos() {
        for (int i = 0; i < photoViews.size(); i++) {
            String url = pickPhoto(i == 0 ? 1 : i + 1);
            if (url != null) photoViews.get(i).setImage(new Image(url, true));
        }
    }
}
