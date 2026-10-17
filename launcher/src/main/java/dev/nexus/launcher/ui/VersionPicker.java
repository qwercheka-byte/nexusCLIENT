package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.game.LoaderType;
import dev.nexus.launcher.game.Loaders;
import dev.nexus.launcher.game.VersionManifest;
import dev.nexus.launcher.game.VersionRepository;
import dev.nexus.launcher.i18n.I18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/** Minecraft version list with filters + loader selector + loader version list. */
final class VersionPicker extends VBox {
    private final ObservableList<VersionManifest.Entry> all = FXCollections.observableArrayList();
    private final FilteredList<VersionManifest.Entry> filtered = new FilteredList<>(all);
    private final ListView<VersionManifest.Entry> list = new ListView<>(filtered);
    private final TextField search = new TextField();
    private final CheckBox snapshots = new CheckBox(I18n.t("versions.snapshots"));
    private final CheckBox old = new CheckBox(I18n.t("versions.old"));
    private final ToggleGroup loaderGroup = new ToggleGroup();
    private final ComboBox<Loaders.LoaderVersion> loaderVersion = new ComboBox<>();
    private final Label loaderStatus = Fx.label("", "faint", "small");
    private final ProgressIndicator loaderSpinner = new ProgressIndicator();
    private final StackPane listHolder = new StackPane();
    private final HBox loaderRow;
    private Runnable onChange = () -> { };
    private int loaderRequest;
    private String wantedMc;
    private String wantedLoaderVersion;

    VersionPicker(String mc, LoaderType loader, String loaderVer) {
        super(10);
        wantedMc = mc;
        wantedLoaderVersion = loaderVer;

        search.setPromptText(I18n.t("versions.search"));
        HBox.setHgrow(search, Priority.ALWAYS);
        snapshots.setSelected(Settings.get().showSnapshots);
        old.setSelected(Settings.get().showOldVersions);
        search.textProperty().addListener((o, a, b) -> applyFilter());
        snapshots.selectedProperty().addListener((o, a, b) -> applyFilter());
        old.selectedProperty().addListener((o, a, b) -> applyFilter());
        HBox filters = new HBox(12, search, snapshots, old);
        filters.setAlignment(Pos.CENTER_LEFT);

        list.getStyleClass().add("version-list");
        list.setPrefHeight(210);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(VersionManifest.Entry e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label id = Fx.label(e.id, "h3");
                String type = e.isRelease() ? I18n.t("versions.release") : e.isSnapshot() ? I18n.t("versions.snapshot") : I18n.t("versions.oldType");
                Label badge = Fx.label(type, "badge", e.isRelease() ? "badge-accent" : e.isSnapshot() ? "badge-warning" : "badge");
                String date = e.releaseTime != null && e.releaseTime.length() >= 10 ? e.releaseTime.substring(0, 10) : "";
                HBox row = new HBox(10, id, badge, Fx.spacer(), Fx.label(date, "faint", "small"));
                row.setAlignment(Pos.CENTER_LEFT);
                setText(null);
                setGraphic(row);
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            loadLoaderVersions();
            onChange.run();
        });
        ProgressIndicator spin = new ProgressIndicator();
        spin.setMaxSize(32, 32);
        listHolder.getChildren().setAll(list, spin);

        HBox segment = new HBox();
        segment.getStyleClass().add("segment");
        for (LoaderType t : LoaderType.values()) {
            ToggleButton b = new ToggleButton(t.display());
            b.getStyleClass().setAll("segment-button");
            b.setUserData(t);
            b.setToggleGroup(loaderGroup);
            if (t == loader) b.setSelected(true);
            segment.getChildren().add(b);
        }
        loaderGroup.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true); // keep one selected
                return;
            }
            loadLoaderVersions();
            onChange.run();
        });

        loaderVersion.setPrefWidth(260);
        loaderVersion.valueProperty().addListener((o, a, b) -> onChange.run());
        loaderSpinner.setMaxSize(18, 18);
        loaderSpinner.setVisible(false);
        loaderRow = new HBox(10, Fx.label(I18n.t("versions.loaderVersion"), "dim"), loaderVersion, loaderSpinner, loaderStatus);
        loaderRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(Fx.label(I18n.t("versions.minecraft"), "section-title"), filters, listHolder,
                Fx.label(I18n.t("versions.loader"), "section-title"), segment, loaderRow);
        VBox.setVgrow(listHolder, Priority.ALWAYS);
        loadManifest(false);
    }

    void setOnChange(Runnable r) {
        onChange = r;
    }

    private void loadManifest(boolean refresh) {
        Fx.async(() -> VersionRepository.manifest(refresh), m -> {
            all.setAll(m.versions);
            listHolder.getChildren().setAll(list);
            VersionManifest.Entry wanted = wantedMc != null ? m.find(wantedMc) : null;
            if (wanted == null && m.latest != null) wanted = m.find(m.latest.release);
            if (wanted != null) {
                if (wanted.isSnapshot()) snapshots.setSelected(true);
                if (wanted.isOld()) old.setSelected(true);
            }
            applyFilter();
            if (wanted != null) {
                list.getSelectionModel().select(wanted);
                list.scrollTo(Math.max(0, filtered.indexOf(wanted) - 2));
            }
        }, err -> {
            Label l = Fx.label(I18n.t("versions.manifestError", Fx.errorText(err)), "danger-text", "small");
            l.setWrapText(true);
            var retry = Fx.button(I18n.t("common.retry"), Icons.REFRESH);
            retry.setOnAction(e -> loadManifest(true));
            VBox v = new VBox(10, l, retry);
            v.setAlignment(Pos.CENTER);
            listHolder.getChildren().setAll(v);
        });
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        VersionManifest.Entry sel = list.getSelectionModel().getSelectedItem();
        filtered.setPredicate(e -> (e.isRelease() || snapshots.isSelected() && e.isSnapshot() || old.isSelected() && e.isOld()
                || !e.isRelease() && !e.isSnapshot() && !e.isOld() && snapshots.isSelected())
                && (q.isEmpty() || e.id.toLowerCase(Locale.ROOT).contains(q)));
        if (sel != null && filtered.contains(sel)) list.getSelectionModel().select(sel);
    }

    private void loadLoaderVersions() {
        LoaderType type = loader();
        String mc = mcVersion();
        int req = ++loaderRequest;
        loaderVersion.getItems().clear();
        loaderStatus.setText("");
        boolean modded = type != LoaderType.VANILLA;
        loaderRow.setVisible(modded);
        loaderRow.setManaged(modded);
        if (!modded || mc == null) {
            loaderSpinner.setVisible(false);
            return;
        }
        loaderSpinner.setVisible(true);
        loaderVersion.setDisable(true);
        Fx.async(() -> Loaders.versions(type, mc), (List<Loaders.LoaderVersion> versions) -> {
            if (req != loaderRequest) return;
            loaderSpinner.setVisible(false);
            loaderVersion.setDisable(versions.isEmpty());
            loaderVersion.getItems().setAll(versions);
            if (versions.isEmpty()) {
                loaderStatus.setText(I18n.t("versions.loaderUnsupported", type.display(), mc));
            } else {
                Loaders.LoaderVersion pick = null;
                for (Loaders.LoaderVersion v : versions) if (v.version().equals(wantedLoaderVersion)) pick = v;
                if (pick == null) pick = versions.stream().filter(v -> v.tag() != null && v.tag().equals(I18n.t("loader.recommended"))).findFirst()
                        .orElse(versions.stream().filter(Loaders.LoaderVersion::stable).findFirst().orElse(versions.get(0)));
                wantedLoaderVersion = null;
                loaderVersion.getSelectionModel().select(pick);
            }
            onChange.run();
        }, err -> {
            if (req != loaderRequest) return;
            loaderSpinner.setVisible(false);
            loaderStatus.setText(I18n.t("versions.loaderError", Fx.errorText(err)));
            onChange.run();
        });
    }

    String mcVersion() {
        VersionManifest.Entry e = list.getSelectionModel().getSelectedItem();
        return e == null ? null : e.id;
    }

    LoaderType loader() {
        return loaderGroup.getSelectedToggle() == null ? LoaderType.VANILLA : (LoaderType) loaderGroup.getSelectedToggle().getUserData();
    }

    String loaderVersion() {
        Loaders.LoaderVersion v = loaderVersion.getValue();
        return v == null ? null : v.version();
    }

    /** A Minecraft version is chosen and, for modded instances, a loader version too. */
    boolean isComplete() {
        return mcVersion() != null && (loader() == LoaderType.VANILLA || loaderVersion() != null);
    }
}
