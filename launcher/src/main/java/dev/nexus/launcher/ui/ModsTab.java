package dev.nexus.launcher.ui;

import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.instance.ModManager;
import dev.nexus.launcher.modrinth.Modrinth;
import dev.nexus.launcher.net.Progress;
import dev.nexus.launcher.update.NexusClientStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Installed mods: enable/disable, delete, add files (button or drag &amp; drop), Modrinth updates. */
final class ModsTab extends VBox implements InstancePage.Tab {
    private final MainWindow window;
    private final Instance inst;
    private final ObservableList<ModManager.ModFile> mods = FXCollections.observableArrayList();
    private final FilteredList<ModManager.ModFile> filtered = new FilteredList<>(mods);
    private final ListView<ModManager.ModFile> list = new ListView<>(filtered);
    private final TextField filter = new TextField();
    private final Label count = Fx.label("", "faint", "small");
    private final Button nexusBtn;
    private final Button updatesBtn;

    ModsTab(MainWindow window, Instance inst) {
        super(12);
        this.window = window;
        this.inst = inst;

        if (!inst.loaderType().supportsMods()) {
            getChildren().add(Fx.emptyState(Icons.PUZZLE, I18n.t("mods.vanilla.title"), I18n.t("mods.vanilla.text")));
            nexusBtn = null;
            updatesBtn = null;
            return;
        }

        filter.setPromptText(I18n.t("mods.filter"));
        filter.setPrefWidth(220);
        filter.textProperty().addListener((o, a, b) -> applyFilter());
        Button add = Fx.button(I18n.t("mods.addFiles"), Icons.ADD);
        add.setOnAction(e -> chooseFiles());
        updatesBtn = Fx.button(I18n.t("mods.checkUpdates"), Icons.REFRESH);
        updatesBtn.setOnAction(e -> checkUpdates());
        Fx.tip(updatesBtn, I18n.t("mods.checkUpdatesTip"));
        Button folder = Fx.iconButton(Icons.FOLDER, I18n.t("mods.openFolder"));
        folder.setOnAction(e -> Fx.openPath(inst.modsDir()));
        nexusBtn = Fx.button(I18n.t("mods.installNexus"), Icons.LOGO, "primary");
        nexusBtn.setOnAction(e -> installNexus());
        HBox toolbar = new HBox(8, filter, count, Fx.spacer(), nexusBtn, add, updatesBtn, folder);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        list.setCellFactory(v -> new ModCell());
        list.setPlaceholder(placeholder());
        VBox.setVgrow(list, Priority.ALWAYS);
        Label dropHint = Fx.label(I18n.t("mods.dropHint"), "faint", "tiny");
        getChildren().addAll(toolbar, list, dropHint);

        setOnDragOver(this::onDragOver);
        setOnDragDropped(this::onDragDropped);
    }

    private VBox placeholder() {
        return Fx.emptyState(Icons.PUZZLE, I18n.t("mods.empty.title"), I18n.t("mods.empty.text"));
    }

    @Override
    public void onShow() {
        reload();
    }

    void reload() {
        if (nexusBtn == null) return;
        Fx.async(() -> {
            List<ModManager.ModFile> list = ModManager.list(inst);
            NexusClientStore.Source best = NexusClientStore.best(inst);
            ModManager.ModFile installed = list.stream().filter(NexusClientStore::isNexus).findFirst().orElse(null);
            boolean outdated = installed != null && best != null && (installed.version() == null
                    || dev.nexus.launcher.game.Loaders.compareVersions(best.version(), installed.version()) > 0);
            return new Object[]{list, best, installed, outdated};
        }, res -> {
            @SuppressWarnings("unchecked") List<ModManager.ModFile> result = (List<ModManager.ModFile>) res[0];
            NexusClientStore.Source best = (NexusClientStore.Source) res[1];
            boolean hasNexus = res[2] != null, outdated = (Boolean) res[3];
            mods.setAll(result);
            applyFilter();
            boolean show = best != null && (!hasNexus || outdated);
            nexusBtn.setText(outdated ? I18n.t("mods.updateNexus", best.version()) : I18n.t("mods.installNexus"));
            nexusBtn.setVisible(show);
            nexusBtn.setManaged(show);
        }, err -> Toasts.error(Fx.errorText(err)));
    }

    private void applyFilter() {
        String q = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(m -> q.isEmpty() || m.displayName().toLowerCase(Locale.ROOT).contains(q)
                || m.fileName().toLowerCase(Locale.ROOT).contains(q));
        long enabled = mods.stream().filter(ModManager.ModFile::enabled).count();
        count.setText(I18n.t("mods.count", mods.size(), enabled));
    }

    private boolean warnIfRunning() {
        if (GameProcess.running(inst) != null) {
            Toasts.info(I18n.t("mods.runningHint"));
            return true;
        }
        return false;
    }

    private void chooseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("mods.addFiles"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("mods.jarFiles"), "*.jar"));
        List<File> files = fc.showOpenMultipleDialog(window.stage());
        if (files != null) addFiles(files.stream().map(File::toPath).toList());
    }

    private void onDragOver(DragEvent e) {
        if (e.getDragboard().hasFiles() && e.getDragboard().getFiles().stream().anyMatch(f -> f.getName().endsWith(".jar"))) {
            e.acceptTransferModes(TransferMode.COPY);
        }
        e.consume();
    }

    private void onDragDropped(DragEvent e) {
        boolean ok = false;
        if (e.getDragboard().hasFiles()) {
            List<Path> jars = e.getDragboard().getFiles().stream().filter(f -> f.getName().endsWith(".jar")).map(File::toPath).toList();
            if (!jars.isEmpty()) {
                addFiles(jars);
                ok = true;
            }
        }
        e.setDropCompleted(ok);
        e.consume();
    }

    private void addFiles(List<Path> files) {
        warnIfRunning();
        Fx.async(() -> {
            for (Path p : files) ModManager.addFile(inst, p);
            return files.size();
        }, n -> {
            Toasts.success(I18n.t("mods.added", n));
            reload();
        }, err -> Toasts.error(Fx.errorText(err)));
    }

    private void installNexus() {
        nexusBtn.setDisable(true);
        Fx.async(() -> {
            NexusClientStore.Source src = NexusClientStore.best(inst);
            if (src == null) throw new java.io.IOException("NEXUS Client is not available for " + inst.mcVersion);
            NexusClientStore.install(inst, src);
            if (!Modrinth.installedProjects(inst).contains(Modrinth.FABRIC_API_ID)
                    && mods.stream().noneMatch(m -> "fabric-api".equals(m.id()))) {
                Modrinth.install(inst, Modrinth.FABRIC_API_ID, "Fabric API", new Progress());
            }
            return true;
        }, x -> {
            nexusBtn.setDisable(false);
            Toasts.success(I18n.t("mods.nexusInstalled"));
            reload();
        }, err -> {
            nexusBtn.setDisable(false);
            Toasts.error(Fx.errorText(err));
            reload();
        });
    }

    private void checkUpdates() {
        updatesBtn.setDisable(true);
        updatesBtn.setText(I18n.t("mods.checking"));
        Fx.async(() -> Modrinth.checkUpdates(inst, new Progress()), updates -> {
            updatesBtn.setDisable(false);
            updatesBtn.setText(I18n.t("mods.checkUpdates"));
            if (updates.isEmpty()) {
                Toasts.info(inst.modrinth.isEmpty() ? I18n.t("mods.noTracked") : I18n.t("mods.upToDate"));
                return;
            }
            showUpdates(updates);
        }, err -> {
            updatesBtn.setDisable(false);
            updatesBtn.setText(I18n.t("mods.checkUpdates"));
            Toasts.error(Fx.errorText(err));
        });
    }

    private void showUpdates(List<Modrinth.Update> updates) {
        Modal m = Modal.of(I18n.t("mods.updatesTitle", updates.size())).width(520);
        VBox rows = new VBox(6);
        List<CheckBox> boxes = new ArrayList<>();
        for (Modrinth.Update u : updates) {
            CheckBox c = new CheckBox(u.title() + "   →   " + u.version().versionNumber());
            c.setSelected(true);
            c.setUserData(u);
            boxes.add(c);
            Label file = Fx.label(u.fileName(), "faint", "tiny");
            rows.getChildren().add(new VBox(2, c, file));
        }
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(rows);
        sp.setFitToWidth(true);
        sp.setPrefHeight(Math.min(320, 46 * updates.size() + 10));
        m.content(sp);
        m.closeButton(I18n.t("common.cancel"), null, null);
        m.closeButton(I18n.t("mods.updateSelected"), "primary", () -> {
            List<Modrinth.Update> chosen = boxes.stream().filter(CheckBox::isSelected).map(c -> (Modrinth.Update) c.getUserData()).toList();
            if (chosen.isEmpty()) return;
            warnIfRunning();
            Toasts.info(I18n.t("mods.updating", chosen.size()));
            Fx.async(() -> {
                for (Modrinth.Update u : chosen) Modrinth.applyUpdate(inst, u, new Progress());
                return chosen.size();
            }, n -> {
                Toasts.success(I18n.t("mods.updated", n));
                reload();
            }, err -> {
                Toasts.error(Fx.errorText(err));
                reload();
            });
        });
        m.show();
    }

    private void toggle(ModManager.ModFile mod, boolean enabled) {
        warnIfRunning();
        Fx.async(() -> ModManager.setEnabled(mod, enabled), p -> reload(), err -> {
            Toasts.error(Fx.errorText(err));
            reload();
        });
    }

    private void delete(ModManager.ModFile mod) {
        Modal.confirm(I18n.t("mods.deleteTitle"), I18n.t("mods.deleteText", mod.displayName()), I18n.t("common.delete"), true, () -> {
            warnIfRunning();
            Fx.async(() -> {
                ModManager.delete(inst, mod);
                InstanceManager.save(inst);
                return true;
            }, x -> reload(), err -> Toasts.error(Fx.errorText(err)));
        });
    }

    private final class ModCell extends ListCell<ModManager.ModFile> {
        ModCell() {
            setStyle("-fx-padding: 0 0 6 0;");
        }

        @Override
        protected void updateItem(ModManager.ModFile mod, boolean empty) {
            super.updateItem(mod, empty);
            setText(null);
            if (empty || mod == null) {
                setGraphic(null);
                return;
            }
            StackPane icon = new StackPane();
            icon.getStyleClass().add("mod-icon");
            icon.setMinSize(40, 40);
            icon.setMaxSize(40, 40);
            Image img = null;
            if (mod.icon() != null) {
                img = new Image(new ByteArrayInputStream(mod.icon()), 40, 40, true, true);
                if (img.isError()) img = null;
            }
            if (img != null) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(40);
                iv.setFitHeight(40);
                javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(40, 40);
                clip.setArcWidth(16);
                clip.setArcHeight(16);
                iv.setClip(clip);
                icon.getChildren().add(iv);
            } else {
                icon.getChildren().add(Icons.icon(Icons.PUZZLE, 20));
            }

            Label name = Fx.label(mod.displayName(), "h3");
            Label version = Fx.label(mod.version() == null ? "" : mod.version(), "badge");
            HBox titleRow = new HBox(8, name, version);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            if (mod.version() == null || mod.version().isBlank()) titleRow.getChildren().remove(version);
            if (inst.modrinth.containsKey(mod.baseName())) {
                titleRow.getChildren().add(Fx.label("Modrinth", "badge", "badge-success"));
            }
            String descText = mod.description() == null || mod.description().isBlank() ? mod.fileName()
                    : mod.description().replace('\n', ' ');
            Label desc = Fx.label(descText, "dim", "small");
            desc.setMaxWidth(560);
            Label file = Fx.label(mod.fileName() + (mod.authors() == null || mod.authors().isBlank() ? "" : "   ·   " + mod.authors()), "faint", "tiny");
            VBox text = new VBox(3, titleRow, desc, file);
            HBox.setHgrow(text, Priority.ALWAYS);

            CheckBox sw = Fx.toggle(mod.enabled());
            Fx.tip(sw, I18n.t(mod.enabled() ? "mods.disable" : "mods.enable"));
            sw.setOnAction(e -> toggle(mod, sw.isSelected()));
            Button del = Fx.iconButton(Icons.DELETE, I18n.t("common.delete"), "ghost", "danger");
            del.setOnAction(e -> delete(mod));

            HBox row = new HBox(12, icon, text, sw, del);
            row.getStyleClass().add("row");
            if (!mod.enabled()) row.getStyleClass().add("disabled-mod");
            row.setOpacity(mod.enabled() ? 1 : 0.7);
            row.setMaxWidth(Double.MAX_VALUE);
            row.prefWidthProperty().bind(list.widthProperty().subtract(22));
            setGraphic(row);
        }
    }
}
