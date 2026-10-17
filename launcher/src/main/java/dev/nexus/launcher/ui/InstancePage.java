package dev.nexus.launcher.ui;

import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** One instance: header with actions and the Mods / Catalog / Settings / Console tabs. */
final class InstancePage extends VBox implements MainWindow.Page {
    private final MainWindow window;
    private final Instance inst;
    private final StackPane tabHolder = new StackPane();
    private final ToggleGroup tabs = new ToggleGroup();
    private final Map<String, Supplier<Node>> factories = new LinkedHashMap<>();
    private final Map<String, Node> created = new LinkedHashMap<>();
    private final Button play = new Button();
    private final Label title = Fx.label("", "h1");
    private final Label subtitle = Fx.label("", "dim");
    private final StackPane tileHolder = new StackPane();
    private final Runnable launchListener = this::updatePlay;

    InstancePage(MainWindow window, Instance inst, String initialTab) {
        this.window = window;
        this.inst = inst;
        getStyleClass().add("page");
        window.select(inst);

        Button back = Fx.iconButton(Icons.BACK, I18n.t("common.back"), "ghost");
        back.setOnAction(e -> window.showInstances());
        VBox titles = new VBox(2, title, subtitle);
        play.getStyleClass().addAll("primary");
        play.setOnAction(e -> {
            if (LaunchController.isPreparing(inst)) LaunchController.cancel();
            else if (GameProcess.running(inst) != null) LaunchController.stop(inst);
            else LaunchController.launch(inst);
        });
        Button folder = Fx.iconButton(Icons.FOLDER, I18n.t("instances.openGameFolder"));
        folder.setOnAction(e -> Fx.openPath(inst.gameDir()));
        Button more = Fx.iconButton(Icons.MORE, null);
        more.setOnAction(e -> {
            MenuItem dup = new MenuItem(I18n.t("instances.duplicate"));
            dup.setOnAction(x -> InstanceActions.duplicate(window, inst));
            MenuItem del = new MenuItem(I18n.t("instances.delete"));
            del.getStyleClass().add("danger");
            del.setOnAction(x -> InstanceActions.delete(window, inst, window::showInstances));
            new ContextMenu(dup, del).show(more, javafx.geometry.Side.BOTTOM, 0, 4);
        });
        HBox header = new HBox(12, back, tileHolder, titles, Fx.spacer(), folder, more, play);
        header.getStyleClass().add("page-header");

        factories.put("mods", () -> new ModsTab(window, inst));
        factories.put("catalog", () -> new CatalogTab(window, inst));
        factories.put("settings", () -> new InstanceSettingsTab(window, inst, this::refreshHeader));
        factories.put("console", () -> new ConsoleTab(window, inst));
        HBox segment = new HBox();
        segment.getStyleClass().add("segment");
        segment.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        add(segment, "mods", I18n.t("tab.mods"), Icons.PUZZLE);
        add(segment, "catalog", I18n.t("tab.catalog"), Icons.SEARCH);
        add(segment, "settings", I18n.t("tab.settings"), Icons.TUNE);
        add(segment, "console", I18n.t("tab.console"), Icons.TERMINAL);
        tabs.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
                return;
            }
            showTab((String) b.getUserData());
        });

        VBox.setVgrow(tabHolder, Priority.ALWAYS);
        getChildren().addAll(header, segment, tabHolder);
        refreshHeader();
        LaunchController.addListener(launchListener);
        updatePlay();
        selectTab(initialTab == null ? "mods" : initialTab);
    }

    private void add(HBox segment, String key, String text, String icon) {
        ToggleButton b = new ToggleButton(text, Icons.icon(icon, 14));
        b.getStyleClass().setAll("segment-button");
        b.setUserData(key);
        b.setToggleGroup(tabs);
        segment.getChildren().add(b);
    }

    void selectTab(String key) {
        tabs.getToggles().stream().filter(t -> key.equals(t.getUserData())).findFirst().ifPresent(t -> t.setSelected(true));
    }

    private void showTab(String key) {
        Node n = created.computeIfAbsent(key, k -> factories.get(k).get());
        tabHolder.getChildren().setAll(n);
        if (n instanceof Tab t) t.onShow();
    }

    void refreshHeader() {
        title.setText(inst.name);
        subtitle.setText(inst.describe() + "   ·   " + (inst.playtimeSeconds > 0
                ? I18n.t("instances.played", Fx.duration(inst.playtimeSeconds)) : I18n.t("instances.neverPlayed")));
        tileHolder.getChildren().setAll(Fx.tile(inst.name, inst.color, 52));
        window.instancesChanged();
    }

    private void updatePlay() {
        boolean running = GameProcess.running(inst) != null;
        boolean preparing = LaunchController.isPreparing(inst);
        play.getStyleClass().removeAll("danger", "primary");
        if (preparing) {
            play.setText(I18n.t("play.cancel"));
            play.setGraphic(Icons.icon(Icons.CLOSE, 14));
        } else if (running) {
            play.setText(I18n.t("play.stop"));
            play.setGraphic(Icons.icon(Icons.STOP, 14));
            play.getStyleClass().add("danger");
        } else {
            play.setText(I18n.t("play.play"));
            play.setGraphic(Icons.icon(Icons.PLAY, 14));
            play.getStyleClass().add("primary");
        }
        play.setDisable(!preparing && !running && LaunchController.preparing() != null);
        if (!running && !preparing) refreshHeaderTextOnly();
        created.values().forEach(n -> {
            if (n instanceof Tab t) t.onLaunchStateChanged();
        });
    }

    private void refreshHeaderTextOnly() {
        subtitle.setText(inst.describe() + "   ·   " + (inst.playtimeSeconds > 0
                ? I18n.t("instances.played", Fx.duration(inst.playtimeSeconds)) : I18n.t("instances.neverPlayed")));
    }

    @Override
    public void onHide() {
        LaunchController.removeListener(launchListener);
        created.values().forEach(n -> {
            if (n instanceof Tab t) t.onHide();
        });
    }

    /** Lifecycle hooks of a tab. */
    interface Tab {
        default void onShow() { }

        default void onHide() { }

        default void onLaunchStateChanged() { }
    }
}
