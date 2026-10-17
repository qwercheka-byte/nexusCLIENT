package dev.nexus.launcher.ui;

import dev.nexus.launcher.update.NexusClientStore;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.game.LoaderType;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.modrinth.Modrinth;
import dev.nexus.launcher.net.Progress;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** "New instance" dialog. The game itself is downloaded on the first launch. */
final class CreateInstanceDialog {
    private CreateInstanceDialog() {}

    static void show(MainWindow window) {
        Modal m = Modal.of(I18n.t("create.title")).width(640);
        TextField name = new TextField();
        name.setPromptText(I18n.t("create.namePrompt"));
        ColorPicker colors = new ColorPicker("violet");
        VersionPicker picker = new VersionPicker(null, LoaderType.VANILLA, null);
        CheckBox nexus = new CheckBox(I18n.t("create.nexus"));
        nexus.setSelected(true);
        Label nexusHint = Fx.label(I18n.t("create.nexusHint"), "faint", "tiny");
        nexusHint.setWrapText(true);
        VBox nexusBox = new VBox(3, nexus, nexusHint);
        Label hint = Fx.label(I18n.t("create.hint"), "faint", "small");
        hint.setWrapText(true);

        HBox nameRow = new HBox(14, new VBox(5, Fx.label(I18n.t("instance.name"), "dim", "small"), name),
                new VBox(9, Fx.label(I18n.t("instance.color"), "dim", "small"), colors));
        HBox.setHgrow(nameRow.getChildren().get(0), javafx.scene.layout.Priority.ALWAYS);
        m.content(nameRow, picker, nexusBox, hint);

        m.closeButton(I18n.t("common.cancel"), null, null);
        Button create = m.button(I18n.t("create.create"), "primary", () -> { });

        String[] autoName = {""};
        Runnable update = () -> {
            boolean nexusOk = NexusClientStore.best(picker.loader(), picker.mcVersion()) != null;
            nexusBox.setVisible(nexusOk);
            nexusBox.setManaged(nexusOk);
            // suggest a name until the user types one
            String suggestion = picker.mcVersion() == null ? "" :
                    (picker.loader() == LoaderType.VANILLA ? "" : picker.loader().display() + " ") + picker.mcVersion();
            if (name.getText().isBlank() || name.getText().equals(autoName[0])) {
                name.setText(suggestion);
                autoName[0] = suggestion;
            }
            create.setDisable(!picker.isComplete());
        };
        picker.setOnChange(update);
        update.run();

        create.setOnAction(e -> {
            String n = name.getText().trim();
            String err = InstanceActions.validateName(n);
            if (err != null) {
                name.getStyleClass().add("invalid");
                Toasts.error(err);
                return;
            }
            String mc = picker.mcVersion();
            LoaderType loader = picker.loader();
            String lv = picker.loaderVersion();
            String color = colors.selected();
            boolean withNexus = nexusBox.isVisible() && nexus.isSelected();
            create.setDisable(true);
            create.setText(I18n.t("create.creating"));
            Fx.async(() -> {
                Instance inst = InstanceManager.create(n, mc, loader, lv, color);
                List<String> warnings = new ArrayList<>();
                if (withNexus) {
                    NexusClientStore.install(inst, NexusClientStore.best(inst));
                    try {
                        Modrinth.install(inst, Modrinth.FABRIC_API_ID, "Fabric API", new Progress());
                    } catch (Exception ex) {
                        Log.warn("Fabric API install failed: " + ex.getMessage());
                        warnings.add(I18n.t("create.fabricApiFailed", Fx.errorText(ex)));
                    }
                }
                return new Object[]{inst, warnings};
            }, res -> {
                Instance inst = (Instance) res[0];
                @SuppressWarnings("unchecked") List<String> warnings = (List<String>) res[1];
                m.close();
                window.select(inst);
                window.instancesChanged();
                Toasts.success(I18n.t("create.done", inst.name));
                warnings.forEach(Toasts::error);
            }, ex -> {
                create.setDisable(false);
                create.setText(I18n.t("create.create"));
                Toasts.error(I18n.t("create.failed", Fx.errorText(ex)));
            });
        });
        name.textProperty().addListener((o, a, b) -> name.getStyleClass().remove("invalid"));
        m.show();
    }

    /** Row of colour dots for the instance tile. */
    static final class ColorPicker extends HBox {
        private String selected;

        ColorPicker(String initial) {
            super(8);
            selected = initial;
            for (String c : List.of("violet", "cyan", "emerald", "rose", "amber", "slate")) {
                Region dot = new Region();
                dot.getStyleClass().addAll("color-dot", "tile-" + c);
                dot.setUserData(c);
                dot.setOnMouseClicked(e -> select(c));
                getChildren().add(dot);
            }
            select(initial);
        }

        void select(String c) {
            selected = c;
            getChildren().forEach(n -> {
                n.getStyleClass().remove("selected");
                if (c.equals(n.getUserData())) n.getStyleClass().add("selected");
            });
        }

        String selected() {
            return selected;
        }
    }
}
