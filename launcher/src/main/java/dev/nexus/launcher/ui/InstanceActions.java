package dev.nexus.launcher.ui;

import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;

/** Actions shared by the instance grid and the instance page. */
final class InstanceActions {
    private InstanceActions() {}

    static String validateName(String name) {
        if (name == null || name.isBlank()) return I18n.t("instance.name.empty");
        if (name.length() > 48) return I18n.t("instance.name.long");
        return null;
    }

    static void duplicate(MainWindow window, Instance inst) {
        Modal.prompt(I18n.t("instances.duplicate"), I18n.t("instance.name"), I18n.t("instances.copyName", inst.name),
                InstanceActions::validateName, name -> Fx.async(() -> InstanceManager.duplicate(inst, name), copy -> {
                    window.instancesChanged();
                    Toasts.success(I18n.t("instances.duplicated", copy.name));
                }, err -> Toasts.error(Fx.errorText(err))));
    }

    static void delete(MainWindow window, Instance inst, Runnable after) {
        if (GameProcess.running(inst) != null || LaunchController.isPreparing(inst)) {
            Toasts.error(I18n.t("instances.deleteRunning"));
            return;
        }
        Modal.confirm(I18n.t("instances.deleteTitle", inst.name), I18n.t("instances.deleteText"), I18n.t("common.delete"), true,
                () -> Fx.async(() -> {
                    try {
                        InstanceManager.delete(inst);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }, () -> {
                    if (window.selectedInstance() == null || inst.id.equals(dev.nexus.launcher.core.Settings.get().selectedInstance)) {
                        window.select(null);
                    }
                    window.instancesChanged();
                    Toasts.success(I18n.t("instances.deleted", inst.name));
                    if (after != null) after.run();
                }, err -> Toasts.error(Fx.errorText(err))));
    }
}
