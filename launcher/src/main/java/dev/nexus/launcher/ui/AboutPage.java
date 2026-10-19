package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.i18n.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

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

        VBox content = new VBox(14, hero,
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

    @Override
    public void onHide() { }
}
