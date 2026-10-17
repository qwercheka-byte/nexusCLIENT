package dev.nexus.launcher.ui;

import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Grid of instances. Click selects (the play bar launches the selection), double click opens. */
final class InstancesPage extends VBox implements MainWindow.Page {
    private final MainWindow window;
    private final FlowPane grid = new FlowPane(14, 14);
    private final TextField filter = new TextField();
    private final Map<String, VBox> cards = new HashMap<>();
    private final Runnable launchListener = this::updateRunning;
    private final StackPane body = new StackPane();

    InstancesPage(MainWindow window) {
        this.window = window;
        getStyleClass().add("page");

        Label title = Fx.label(I18n.t("instances.title"), "h1");
        filter.setPromptText(I18n.t("instances.filter"));
        filter.getStyleClass().add("search-field");
        filter.setPrefWidth(240);
        filter.textProperty().addListener((o, a, b) -> refresh());
        StackPane search = new StackPane(filter, searchIcon());
        StackPane.setAlignment(search.getChildren().get(1), Pos.CENTER_LEFT);
        var refreshBtn = Fx.iconButton(Icons.REFRESH, I18n.t("instances.reload"));
        refreshBtn.setOnAction(e -> {
            InstanceManager.reload();
            window.instancesChanged();
        });
        var folderBtn = Fx.iconButton(Icons.FOLDER, I18n.t("instances.openFolder"));
        folderBtn.setOnAction(e -> Fx.openPath(dev.nexus.launcher.core.LauncherDirs.instances()));
        var create = Fx.button(I18n.t("instances.create"), Icons.ADD, "primary");
        create.setOnAction(e -> CreateInstanceDialog.show(window));
        HBox header = new HBox(10, title, Fx.spacer(), search, refreshBtn, folderBtn, create);
        header.getStyleClass().add("page-header");

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        grid.setStyle("-fx-padding: 2 4 12 0;");
        body.getChildren().add(scroll);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(header, body);
        LaunchController.addListener(launchListener);
    }

    private static Region searchIcon() {
        Region r = Icons.icon(Icons.SEARCH, 15);
        r.setStyle("-fx-background-color: -nx-text-faint;");
        StackPane.setMargin(r, new javafx.geometry.Insets(0, 0, 0, 10));
        return r;
    }

    void refresh() {
        grid.getChildren().clear();
        cards.clear();
        String q = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
        List<Instance> list = InstanceManager.all().stream()
                .filter(i -> q.isEmpty() || i.name.toLowerCase(Locale.ROOT).contains(q) || i.mcVersion.contains(q)
                        || i.loaderType().display().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparingLong((Instance i) -> -Math.max(i.lastPlayed, i.created)))
                .toList();
        if (InstanceManager.all().isEmpty()) {
            VBox empty = Fx.emptyState(Icons.GRID, I18n.t("instances.empty.title"), I18n.t("instances.empty.text"));
            var b = Fx.button(I18n.t("instances.create"), Icons.ADD, "primary");
            b.setOnAction(e -> CreateInstanceDialog.show(window));
            empty.getChildren().add(b);
            body.getChildren().setAll(empty);
            return;
        }
        if (!(body.getChildren().get(0) instanceof ScrollPane)) {
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            body.getChildren().setAll(scroll);
        }
        for (Instance inst : list) {
            VBox card = card(inst);
            cards.put(inst.id, card);
            grid.getChildren().add(card);
        }
        updateSelection();
        updateRunning();
    }

    private VBox card(Instance inst) {
        VBox card = new VBox();
        card.getStyleClass().add("instance-card");
        card.setPrefWidth(250);
        card.setMinWidth(250);
        card.setMaxWidth(250);

        Label name = Fx.label(inst.name, "h3");
        name.setMaxWidth(150);
        Label ver = Fx.label(inst.describe(), "dim", "small");
        ver.setMaxWidth(150);
        VBox text = new VBox(3, name, ver);
        HBox top = new HBox(12, Fx.tile(inst.name, inst.color, 50), text);
        top.setAlignment(Pos.CENTER_LEFT);

        Region dot = new Region();
        dot.getStyleClass().add("running-dot");
        dot.setVisible(false);
        dot.setManaged(false);
        dot.setId("dot");
        Label stats = Fx.label(inst.playtimeSeconds > 0
                ? I18n.t("instances.played", Fx.duration(inst.playtimeSeconds))
                : I18n.t("instances.neverPlayed"), "faint", "tiny");
        stats.setId("stats");
        var play = Fx.iconButton(Icons.PLAY, I18n.t("play.play"), "primary");
        play.setId("play");
        play.setOnAction(e -> {
            window.select(inst);
            if (GameProcess.running(inst) != null) LaunchController.stop(inst);
            else LaunchController.launch(inst);
        });
        var open = Fx.iconButton(Icons.TUNE, I18n.t("instances.open"), "ghost");
        open.setOnAction(e -> window.showInstance(inst, "mods"));
        HBox bottom = new HBox(8, dot, stats, Fx.spacer(), open, play);
        bottom.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(top, bottom);
        card.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() == 2) window.showInstance(inst, "mods");
            else window.select(inst);
        });
        card.setOnContextMenuRequested(e -> {
            window.select(inst);
            menu(inst).show(card, e.getScreenX(), e.getScreenY());
        });
        return card;
    }

    private ContextMenu menu(Instance inst) {
        MenuItem play = new MenuItem(I18n.t("play.play"));
        play.setOnAction(e -> LaunchController.launch(inst));
        MenuItem open = new MenuItem(I18n.t("instances.open"));
        open.setOnAction(e -> window.showInstance(inst, "mods"));
        MenuItem folder = new MenuItem(I18n.t("instances.openGameFolder"));
        folder.setOnAction(e -> Fx.openPath(inst.gameDir()));
        MenuItem dup = new MenuItem(I18n.t("instances.duplicate"));
        dup.setOnAction(e -> InstanceActions.duplicate(window, inst));
        MenuItem del = new MenuItem(I18n.t("instances.delete"));
        del.getStyleClass().add("danger");
        del.setOnAction(e -> InstanceActions.delete(window, inst, null));
        return new ContextMenu(play, open, folder, new SeparatorMenuItem(), dup, del);
    }

    void updateSelection() {
        Instance sel = window.selectedInstance();
        cards.forEach((id, card) -> {
            card.getStyleClass().remove("selected");
            if (sel != null && sel.id.equals(id)) card.getStyleClass().add("selected");
        });
    }

    private void updateRunning() {
        for (Instance inst : InstanceManager.all()) {
            VBox card = cards.get(inst.id);
            if (card == null) continue;
            boolean running = GameProcess.running(inst) != null;
            boolean preparing = LaunchController.isPreparing(inst);
            Region dot = (Region) card.lookup("#dot");
            Label stats = (Label) card.lookup("#stats");
            var play = (javafx.scene.control.Button) card.lookup("#play");
            dot.setVisible(running);
            dot.setManaged(running);
            if (running) stats.setText(I18n.t("instances.running"));
            else if (preparing) stats.setText(I18n.t("instances.preparing"));
            else stats.setText(inst.playtimeSeconds > 0 ? I18n.t("instances.played", Fx.duration(inst.playtimeSeconds)) : I18n.t("instances.neverPlayed"));
            play.setGraphic(Icons.icon(running ? Icons.STOP : Icons.PLAY, 17));
            play.setDisable(preparing || (!running && LaunchController.preparing() != null));
        }
    }

    @Override
    public void onHide() {
        // the page object is reused; keep the listener
    }
}
