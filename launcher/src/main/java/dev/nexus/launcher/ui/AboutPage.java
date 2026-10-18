package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.util.Comparator;
import java.util.List;

import static dev.nexus.launcher.ui.InstanceSettingsTab.card;
import static dev.nexus.launcher.ui.InstanceSettingsTab.hint;

/** Version, environment, credits and the non-affiliation notice. */
final class AboutPage extends VBox implements MainWindow.Page {

    AboutPage(MainWindow window) {
        getStyleClass().add("page");

        Label name = Fx.label(BuildInfo.NAME, "h1");
        Label version = Fx.label(I18n.t("about.version", BuildInfo.VERSION), "badge", "badge-accent");
        Label tagline = Fx.label(I18n.t("about.tagline"), "dim");
        tagline.setWrapText(true);
        VBox titles = new VBox(6, Fx.row(10, name, version), tagline);
        HBox hero = new HBox(18, MainWindow.logoNode(64), titles);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().add("card");

        GridPane env = new GridPane();
        env.setHgap(18);
        env.setVgap(6);
        String[][] rows = {
                {I18n.t("about.java"), System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")"},
                {"JavaFX", BuildInfo.JAVAFX},
                {I18n.t("about.os"), System.getProperty("os.name") + " " + System.getProperty("os.version") + " · " + OS.ARCH},
                {I18n.t("about.memory"), OS.totalMemoryMb() > 0 ? InstanceSettingsTab.MemoryControl.fmt(OS.totalMemoryMb()) : "?"},
                {I18n.t("about.dataDir"), LauncherDirs.root().toString()},
        };
        for (int i = 0; i < rows.length; i++) {
            env.add(Fx.label(rows[i][0], "dim"), 0, i);
            Label v = Fx.label(rows[i][1], "mono");
            v.setWrapText(true);
            env.add(v, 1, i);
        }

        VBox features = new VBox(4);
        for (String k : new String[]{"about.f1", "about.f2", "about.f3", "about.f4", "about.f5"}) {
            var check = Icons.icon(Icons.CHECK, 15);
            check.setStyle("-fx-background-color: -nx-accent;");
            features.getChildren().add(Fx.row(8, check, Fx.label(I18n.t(k), "dim")));
        }

        Button modrinth = Fx.button("Modrinth", Icons.OPEN, "ghost");
        modrinth.setOnAction(e -> window.openLink("https://modrinth.com"));
        Button logs = Fx.button(I18n.t("settings.openLogs"), Icons.TERMINAL, "ghost");
        logs.setOnAction(e -> Fx.openPath(LauncherDirs.logs()));

        FlowPane packs = new FlowPane(16, 16);
        packs.getChildren().addAll(
                packTile(window, "fresh-animations", "Fresh Animations", "respacks.fresh", "fresh-animations"),
                packTile(window, "bare-bones", "Bare Bones", "respacks.bare", "bare-bones"),
                packTile(window, "better-leaves", "Motschen's Better Leaves", "respacks.leaves", "better-leaves"),
                packTile(window, "dramatic-skys", "Dramatic Skys", "respacks.skys", "dramatic-skys"));

        Button openPacks = Fx.button(I18n.t("respacks.open"), Icons.FOLDER, "primary");
        openPacks.setOnAction(e -> openResourcepacks());
        Button morePacks = Fx.button(I18n.t("respacks.more"), Icons.OPEN, "ghost");
        morePacks.setOnAction(e -> window.openLink("https://modrinth.com/resourcepacks"));

        VBox content = new VBox(14, hero,
                card(I18n.t("respacks.title"), hint(I18n.t("respacks.sub")), packs),
                card(I18n.t("respacks.howTitle"),
                        hint(I18n.t("respacks.how1")),
                        hint(I18n.t("respacks.how2")),
                        hint(I18n.t("respacks.how3")),
                        Fx.row(8, openPacks, morePacks)),
                card(I18n.t("about.features"), features),
                card(I18n.t("about.environment"), env, Fx.row(8, logs)),
                card(I18n.t("about.credits"),
                        hint(I18n.t("about.creditsText")), Fx.row(8, modrinth)),
                card(I18n.t("about.legal"), hint(I18n.t("about.legalText"))));
        content.setMaxWidth(860);
        content.setStyle("-fx-padding: 0 10 10 0;");
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().add(sp);
    }

    /** Clickable recommendation tile: pack screenshot on top, name and short description below. */
    private Node packTile(MainWindow window, String img, String name, String descKey, String slug) {
        ImageView iv = new ImageView(new Image(getClass()
                .getResourceAsStream("/dev/nexus/launcher/img/packs/" + img + ".jpg")));
        iv.setFitWidth(300);
        iv.setFitHeight(169);
        Rectangle clip = new Rectangle(300, 169);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        iv.setClip(clip);

        Label title = Fx.label(name, "strong");
        Label desc = Fx.label(I18n.t(descKey), "faint", "tiny");
        desc.setWrapText(true);
        desc.setMaxWidth(300);

        VBox box = new VBox(8, iv, title, desc);
        box.setMaxWidth(312);
        box.setPadding(new Insets(6));
        box.setCursor(Cursor.HAND);
        String base = "-fx-background-radius: 12; -fx-border-radius: 12; -fx-border-width: 1.5; -fx-border-color: transparent;";
        box.setStyle(base);
        box.setOnMouseEntered(e -> box.setStyle("-fx-border-color: -nx-accent; " + base));
        box.setOnMouseExited(e -> box.setStyle(base));
        box.setOnMouseClicked(e -> window.openLink("https://modrinth.com/resourcepack/" + slug));
        return box;
    }

    /** Opens the resourcepacks folder of the most recently played instance. */
    private void openResourcepacks() {
        List<Instance> all = InstanceManager.all();
        if (all.isEmpty()) {
            Toasts.error(I18n.t("respacks.noInstance"));
            return;
        }
        Instance best = all.stream().max(Comparator.comparingLong(i -> i.lastPlayed)).orElse(all.get(0));
        Fx.openPath(best.gameDir().resolve("resourcepacks"));
    }

    @Override
    public void onHide() { }
}
