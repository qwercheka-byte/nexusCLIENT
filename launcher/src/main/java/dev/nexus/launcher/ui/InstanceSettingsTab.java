package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.OS;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.game.LoaderType;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Objects;

/** Per-instance settings. Empty fields / unchecked overrides fall back to the global settings. */
final class InstanceSettingsTab extends VBox implements InstancePage.Tab {
    private final Instance inst;
    private final Runnable onSaved;
    private final MainWindow window;

    private final TextField name = new TextField();
    private final CreateInstanceDialog.ColorPicker color;
    private VersionPicker picker;
    private final VBox versionBox = new VBox(10);
    private final CheckBox memOverride = new CheckBox(I18n.t("isettings.memOverride"));
    private final MemoryControl memory;
    private final TextField javaPath = new TextField();
    private final TextField jvmArgs = new TextField();
    private final TextField width = new TextField();
    private final TextField height = new TextField();
    private final CheckBox fullscreen = new CheckBox(I18n.t("isettings.fullscreen"));
    private final TextField server = new TextField();

    InstanceSettingsTab(MainWindow window, Instance inst, Runnable onSaved) {
        super(12);
        this.window = window;
        this.inst = inst;
        this.onSaved = onSaved;

        name.setText(inst.name);
        color = new CreateInstanceDialog.ColorPicker(inst.color);

        Label curVersion = Fx.label(inst.describe(), "h3");
        versionBox.getChildren().setAll(Fx.row(12, curVersion, versionChangeButton()));

        memOverride.setSelected(inst.maxMemoryMb != null);
        Settings g = Settings.get();
        memory = new MemoryControl(inst.minMemoryMb != null ? inst.minMemoryMb : g.minMemoryMb,
                inst.maxMemoryMb != null ? inst.maxMemoryMb : g.maxMemoryMb);
        memory.disableProperty().bind(memOverride.selectedProperty().not());

        javaPath.setText(Objects.requireNonNullElse(inst.javaPath, ""));
        javaPath.setPromptText(I18n.t("isettings.javaAuto"));
        HBox.setHgrow(javaPath, Priority.ALWAYS);
        Button browse = Fx.button(I18n.t("common.browse"), Icons.FOLDER);
        browse.setOnAction(e -> {
            File f = chooseJava(window);
            if (f != null) javaPath.setText(f.getAbsolutePath());
        });
        jvmArgs.setText(Objects.requireNonNullElse(inst.jvmArgs, ""));
        jvmArgs.setPromptText(I18n.t("isettings.jvmGlobal"));

        width.setText(inst.width == null ? "" : inst.width.toString());
        height.setText(inst.height == null ? "" : inst.height.toString());
        width.setPromptText("854");
        height.setPromptText("480");
        width.setPrefWidth(90);
        height.setPrefWidth(90);
        fullscreen.setSelected(inst.fullscreen);
        server.setText(Objects.requireNonNullElse(inst.autoJoinServer, ""));
        server.setPromptText("play.example.net:25565");

        Button save = Fx.button(I18n.t("common.save"), Icons.CHECK, "primary");
        save.setOnAction(e -> save());
        Button reset = Fx.button(I18n.t("isettings.reinstall"), Icons.REFRESH);
        Fx.tip(reset, I18n.t("isettings.reinstallTip"));
        reset.setOnAction(e -> {
            inst.versionId = null;
            saveQuietly();
            Toasts.info(I18n.t("isettings.reinstallDone"));
        });

        VBox content = new VBox(14,
                card(I18n.t("isettings.general"),
                        field(I18n.t("instance.name"), name),
                        field(I18n.t("instance.color"), color)),
                card(I18n.t("isettings.version"), versionBox),
                card(I18n.t("isettings.memory"), memOverride, memory),
                card(I18n.t("isettings.java"),
                        field(I18n.t("isettings.javaPath"), Fx.row(8, javaPath, browse)),
                        field(I18n.t("isettings.jvmArgs"), jvmArgs)),
                card(I18n.t("isettings.window"),
                        Fx.row(10, Fx.label(I18n.t("isettings.resolution"), "dim"), width, Fx.label("×", "dim"), height, fullscreen),
                        field(I18n.t("isettings.autoJoin"), server),
                        hint(I18n.t("isettings.autoJoinHint"))));
        content.setStyle("-fx-padding: 0 10 10 0;");
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);
        HBox actions = new HBox(10, reset, Fx.spacer(), save);
        actions.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(sp, actions);
    }

    static File chooseJava(MainWindow window) {
        FileChooser fc = new FileChooser();
        fc.setTitle(I18n.t("isettings.javaPath"));
        if (OS.isWindows()) fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("java.exe / javaw.exe", "java.exe", "javaw.exe"));
        return fc.showOpenDialog(window.stage());
    }

    static VBox card(String title, Node... nodes) {
        VBox v = new VBox(10);
        v.getStyleClass().add("card");
        v.getChildren().add(Fx.label(title.toUpperCase(), "section-title"));
        v.getChildren().addAll(nodes);
        return v;
    }

    static VBox field(String label, Node control) {
        return new VBox(5, Fx.label(label, "dim", "small"), control);
    }

    static Label hint(String text) {
        Label l = Fx.label(text, "faint", "tiny");
        l.setWrapText(true);
        return l;
    }

    private Integer parseDim(TextField f, String what) {
        String s = f.getText().trim();
        if (s.isEmpty()) return null;
        try {
            int v = Integer.parseInt(s);
            if (v < 100 || v > 16384) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(I18n.t("isettings.badResolution", what));
        }
    }

    private void save() {
        String n = name.getText().trim();
        String err = InstanceActions.validateName(n);
        if (err != null) {
            Toasts.error(err);
            return;
        }
        Integer w, h;
        try {
            w = parseDim(width, I18n.t("isettings.width"));
            h = parseDim(height, I18n.t("isettings.height"));
        } catch (IllegalArgumentException e) {
            Toasts.error(e.getMessage());
            return;
        }
        String jp = javaPath.getText().trim();
        if (!jp.isEmpty() && !new File(jp).isFile()) {
            Toasts.error(I18n.t("isettings.javaMissing"));
            return;
        }
        if (picker != null) {
            if (!picker.isComplete()) {
                Toasts.error(I18n.t("isettings.versionIncomplete"));
                return;
            }
            if (GameProcess.running(inst) != null || LaunchController.isPreparing(inst)) {
                Toasts.error(I18n.t("isettings.runningNoVersionChange"));
                return;
            }
            LoaderType lt = picker.loader();
            String lv = lt == LoaderType.VANILLA ? null : picker.loaderVersion();
            boolean changed = !picker.mcVersion().equals(inst.mcVersion) || lt != inst.loaderType() || !Objects.equals(lv, inst.loaderVersion);
            if (changed) {
                inst.mcVersion = picker.mcVersion();
                inst.loader = lt.name();
                inst.loaderVersion = lv;
                inst.versionId = null;
            }
        }
        inst.name = n;
        inst.color = color.selected();
        if (memOverride.isSelected()) {
            inst.minMemoryMb = memory.min();
            inst.maxMemoryMb = memory.max();
        } else {
            inst.minMemoryMb = null;
            inst.maxMemoryMb = null;
        }
        inst.javaPath = jp.isEmpty() ? null : jp;
        inst.jvmArgs = jvmArgs.getText().isBlank() ? null : jvmArgs.getText().trim();
        inst.width = w;
        inst.height = h;
        inst.fullscreen = fullscreen.isSelected();
        inst.autoJoinServer = server.getText().isBlank() ? null : server.getText().trim();
        if (saveQuietly()) {
            Toasts.success(I18n.t("isettings.saved"));
            if (picker != null) {
                picker = null;
                versionBox.getChildren().setAll(Fx.row(12, Fx.label(inst.describe(), "h3"), versionChangeButton()));
            }
            onSaved.run();
        }
    }

    private Button versionChangeButton() {
        Button change = Fx.button(I18n.t("isettings.changeVersion"), Icons.EDIT);
        change.setOnAction(e -> {
            picker = new VersionPicker(inst.mcVersion, inst.loaderType(), inst.loaderVersion);
            picker.setPrefHeight(380);
            Label warn = hint(I18n.t("isettings.changeWarn"));
            versionBox.getChildren().setAll(picker, warn);
        });
        return change;
    }

    private boolean saveQuietly() {
        try {
            InstanceManager.save(inst);
            return true;
        } catch (Exception e) {
            Toasts.error(Fx.errorText(e));
            return false;
        }
    }

    /** Min/max memory sliders (MB), max capped by the physical memory. */
    static final class MemoryControl extends VBox {
        private final javafx.scene.control.Slider minS;
        private final javafx.scene.control.Slider maxS;

        MemoryControl(int min, int max) {
            super(8);
            long total = OS.totalMemoryMb();
            int cap = (int) Math.max(2048, total > 0 ? total - 512 : 16384);
            cap = Math.min(cap, 65536);
            minS = new javafx.scene.control.Slider(256, cap, Math.min(min, cap));
            maxS = new javafx.scene.control.Slider(512, cap, Math.min(max, cap));
            for (var s : new javafx.scene.control.Slider[]{minS, maxS}) {
                s.setBlockIncrement(256);
                s.setMajorTickUnit(256);
                s.setMinorTickCount(0);
                s.setSnapToTicks(true);
                HBox.setHgrow(s, Priority.ALWAYS);
            }
            Label minL = Fx.label("", "mono");
            Label maxL = Fx.label("", "mono");
            minL.setMinWidth(80);
            maxL.setMinWidth(80);
            Runnable upd = () -> {
                if (minS.getValue() > maxS.getValue()) minS.setValue(maxS.getValue());
                minL.setText(fmt(minS.getValue()));
                maxL.setText(fmt(maxS.getValue()));
            };
            minS.valueProperty().addListener((o, a, b) -> {
                if (b.doubleValue() > maxS.getValue()) maxS.setValue(b.doubleValue());
                upd.run();
            });
            maxS.valueProperty().addListener((o, a, b) -> upd.run());
            upd.run();
            Label lMin = Fx.label(I18n.t("memory.min"), "dim");
            Label lMax = Fx.label(I18n.t("memory.max"), "dim");
            lMin.setMinWidth(130);
            lMax.setMinWidth(130);
            lMin.setPrefWidth(130);
            lMax.setPrefWidth(130);
            String info = total > 0 ? I18n.t("memory.total", fmt(total), fmt(Settings.recommendedMaxMemory())) : "";
            getChildren().addAll(Fx.row(12, lMax, maxS, maxL), Fx.row(12, lMin, minS, minL), hint(info));
        }

        static String fmt(double mb) {
            return mb >= 1024 ? String.format(java.util.Locale.ROOT, "%.1f GB", mb / 1024.0).replace(".0 GB", " GB") : (int) mb + " MB";
        }

        int min() { return (int) Math.round(minS.getValue()); }

        int max() { return (int) Math.round(maxS.getValue()); }
    }
}
