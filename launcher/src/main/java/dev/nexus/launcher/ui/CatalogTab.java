package dev.nexus.launcher.ui;

import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.modrinth.Modrinth;
import dev.nexus.launcher.net.Progress;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.HashSet;
import java.util.Set;

/** Modrinth search filtered to the instance's loader and Minecraft version. */
final class CatalogTab extends VBox implements InstancePage.Tab {
    private static final int PAGE = 20;

    private final MainWindow window;
    private final Instance inst;
    private final TextField query = new TextField();
    private final ComboBox<Modrinth.Sort> sort = new ComboBox<>();
    private final VBox results = new VBox(6);
    private final Label status = Fx.label("", "faint", "small");
    private final Button more = Fx.button(I18n.t("catalog.more"), null);
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(400));
    private final Set<String> installing = new HashSet<>();
    private Set<String> installed = new HashSet<>();
    /** Mod ids read from the jars (catches mods added by hand whose id equals the Modrinth slug). */
    private Set<String> installedModIds = new HashSet<>();
    private int offset;
    private int request;
    private boolean loadedOnce;

    CatalogTab(MainWindow window, Instance inst) {
        super(12);
        this.window = window;
        this.inst = inst;

        if (!inst.loaderType().supportsMods()) {
            getChildren().add(Fx.emptyState(Icons.SEARCH, I18n.t("mods.vanilla.title"), I18n.t("mods.vanilla.text")));
            return;
        }

        query.setPromptText(I18n.t("catalog.search"));
        HBox.setHgrow(query, Priority.ALWAYS);
        query.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        debounce.setOnFinished(e -> search(true));
        sort.getItems().setAll(Modrinth.Sort.values());
        sort.setValue(Modrinth.Sort.RELEVANCE);
        sort.setCellFactory(v -> sortCell());
        sort.setButtonCell(sortCell());
        sort.valueProperty().addListener((o, a, b) -> search(true));
        Label filterInfo = Fx.label(I18n.t("catalog.filterInfo", inst.loaderType().display(), inst.mcVersion), "badge", "badge-accent");
        HBox toolbar = new HBox(10, query, sort, filterInfo);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        spinner.setMaxSize(26, 26);
        spinner.setVisible(false);
        more.setOnAction(e -> search(false));
        more.setVisible(false);
        HBox footer = new HBox(12, more, spinner, status);
        footer.setAlignment(Pos.CENTER);
        footer.setStyle("-fx-padding: 6 0 10 0;");
        VBox content = new VBox(6, results, footer);
        content.setStyle("-fx-padding: 0 8 0 0;");
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);
        Label credit = Fx.label(I18n.t("catalog.credit"), "faint", "tiny");
        getChildren().addAll(toolbar, sp, credit);
    }

    private static ListCell<Modrinth.Sort> sortCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Modrinth.Sort s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : I18n.t("catalog.sort." + s.name().toLowerCase(java.util.Locale.ROOT)));
            }
        };
    }

    @Override
    public void onShow() {
        if (!inst.loaderType().supportsMods()) return;
        installed = Modrinth.installedProjects(inst);
        Fx.async(() -> {
            Set<String> ids = new HashSet<>();
            for (var m : dev.nexus.launcher.instance.ModManager.list(inst)) if (m.id() != null) ids.add(m.id());
            return ids;
        }, ids -> {
            installedModIds = ids;
            if (!loadedOnce) {
                loadedOnce = true;
                search(true);
            }
        }, err -> {
            if (!loadedOnce) {
                loadedOnce = true;
                search(true);
            }
        });
    }

    private void search(boolean reset) {
        if (reset) {
            offset = 0;
            results.getChildren().clear();
        }
        int req = ++request;
        spinner.setVisible(true);
        more.setVisible(false);
        status.setText("");
        String q = query.getText() == null ? "" : query.getText().trim();
        Modrinth.Sort s = sort.getValue();
        int from = offset;
        Fx.async(() -> Modrinth.search(q, inst, s, from, PAGE), res -> {
            if (req != request) return;
            spinner.setVisible(false);
            for (Modrinth.Hit h : res.hits()) results.getChildren().add(row(h));
            offset = from + res.hits().size();
            more.setVisible(offset < res.total() && !res.hits().isEmpty());
            if (results.getChildren().isEmpty()) status.setText(I18n.t("catalog.nothing"));
            else status.setText(I18n.t("catalog.shown", offset, res.total()));
        }, err -> {
            if (req != request) return;
            spinner.setVisible(false);
            status.setText(I18n.t("catalog.error", Fx.errorText(err)));
            more.setText(I18n.t("common.retry"));
            more.setVisible(true);
        });
    }

    private HBox row(Modrinth.Hit h) {
        StackPane icon = new StackPane(Icons.icon(Icons.PUZZLE, 22));
        icon.getStyleClass().add("mod-icon");
        icon.setMinSize(48, 48);
        icon.setMaxSize(48, 48);
        if (h.iconUrl() != null && !h.iconUrl().isBlank()) {
            // JavaFX cannot decode WebP (most Modrinth icons); many projects still have a legacy icon.png.
            loadIcon(icon, h.iconUrl(), "https://cdn.modrinth.com/data/" + h.projectId() + "/icon.png");
        }
        Label title = Fx.label(h.title(), "h3");
        Label author = Fx.label(I18n.t("catalog.by", h.author()), "faint", "small");
        HBox titleRow = new HBox(8, title, author);
        titleRow.setAlignment(Pos.BASELINE_LEFT);
        Label desc = Fx.label(h.description(), "dim", "small");
        desc.setWrapText(true);
        desc.setMaxHeight(36);
        Label downloads = Fx.label(I18n.t("catalog.downloads", compact(h.downloads())), "faint", "tiny");
        VBox text = new VBox(3, titleRow, desc, downloads);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button page = Fx.iconButton(Icons.OPEN, I18n.t("catalog.openPage"), "ghost");
        page.setOnAction(e -> window.openLink("https://modrinth.com/mod/" + (h.slug() != null ? h.slug() : h.projectId())));
        Button install = new Button();
        install.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        updateInstallButton(install, h);
        install.setOnAction(e -> install(h, install));

        HBox row = new HBox(12, icon, text, page, install);
        row.getStyleClass().add("row");
        return row;
    }

    private static void loadIcon(StackPane holder, String url, String fallback) {
        Image img = new Image(url, 48, 48, true, true, true);
        boolean[] done = {false};
        Runnable check = () -> {
            if (done[0]) return;
            if (img.isError()) {
                done[0] = true;
                if (fallback != null && !fallback.equals(url)) loadIcon(holder, fallback, null);
            } else if (img.getProgress() >= 1) {
                done[0] = true;
                ImageView iv = new ImageView(img);
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(48, 48);
                clip.setArcWidth(18);
                clip.setArcHeight(18);
                iv.setClip(clip);
                holder.getChildren().setAll(iv);
            }
        };
        img.progressProperty().addListener((o, a, b) -> check.run());
        img.errorProperty().addListener((o, a, b) -> check.run());
        check.run();
    }

    private void updateInstallButton(Button b, Modrinth.Hit h) {
        b.getStyleClass().removeAll("primary");
        if (installing.contains(h.projectId())) {
            b.setText(I18n.t("catalog.installing"));
            b.setGraphic(null);
            b.setDisable(true);
        } else if (installed.contains(h.projectId()) || h.slug() != null && installedModIds.contains(h.slug())) {
            b.setText(I18n.t("catalog.installed"));
            b.setGraphic(Icons.icon(Icons.CHECK, 14));
            b.setDisable(true);
        } else {
            b.setText(I18n.t("catalog.install"));
            b.setGraphic(Icons.icon(Icons.DOWNLOAD, 14));
            b.getStyleClass().add("primary");
            b.setDisable(false);
        }
    }

    private void install(Modrinth.Hit h, Button b) {
        installing.add(h.projectId());
        updateInstallButton(b, h);
        Fx.async(() -> Modrinth.install(inst, h.projectId(), h.title(), new Progress()), titles -> {
            installing.remove(h.projectId());
            installed = Modrinth.installedProjects(inst);
            installed.add(h.projectId());
            updateInstallButton(b, h);
            if (titles.size() > 1) Toasts.success(I18n.t("catalog.installedWithDeps", h.title(), String.join(", ", titles.subList(1, titles.size()))));
            else Toasts.success(I18n.t("catalog.installedOne", h.title()));
        }, err -> {
            installing.remove(h.projectId());
            updateInstallButton(b, h);
            Toasts.error(I18n.t("catalog.installFailed", h.title(), Fx.errorText(err)));
        });
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return Long.toString(n);
    }
}
