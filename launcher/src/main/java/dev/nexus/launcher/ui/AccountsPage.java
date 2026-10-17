package dev.nexus.launcher.ui;

import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.auth.AccountStore;
import dev.nexus.launcher.auth.MicrosoftAuth;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Accounts: Microsoft (device code flow) and offline profiles. */
final class AccountsPage extends VBox implements MainWindow.Page {
    static final Pattern OFFLINE_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final MainWindow window;
    private final VBox list = new VBox(8);

    AccountsPage(MainWindow window) {
        this.window = window;
        getStyleClass().add("page");

        Button ms = Fx.button(I18n.t("accounts.addMicrosoft"), Icons.MICROSOFT, "primary");
        ms.setOnAction(e -> addMicrosoft());
        Button offline = Fx.button(I18n.t("accounts.addOffline"), Icons.PERSON);
        offline.setOnAction(e -> addOffline());
        HBox header = new HBox(10, Fx.label(I18n.t("accounts.title"), "h1"), Fx.spacer(), offline, ms);
        header.getStyleClass().add("page-header");

        Label info = Fx.label(I18n.t("accounts.info"), "faint", "small");
        info.setWrapText(true);
        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        list.setStyle("-fx-padding: 0 8 0 0;");
        VBox.setVgrow(sp, Priority.ALWAYS);
        getChildren().addAll(header, sp, info);
        refresh();
    }

    private void refresh() {
        list.getChildren().clear();
        Account active = AccountStore.active();
        if (AccountStore.all().isEmpty()) {
            list.getChildren().add(Fx.emptyState(Icons.PERSON, I18n.t("accounts.empty.title"), I18n.t("accounts.empty.text")));
        }
        for (Account a : AccountStore.all()) {
            boolean isActive = active != null && AccountStore.key(a).equals(AccountStore.key(active));
            Label name = Fx.label(a.name, "h3");
            Label type = Fx.label(I18n.t(a.isMicrosoft() ? "account.microsoft" : "account.offline"), "badge", a.isMicrosoft() ? "badge-accent" : "badge");
            HBox titleRow = new HBox(8, name, type);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            if (isActive) titleRow.getChildren().add(Fx.label(I18n.t("accounts.active"), "badge", "badge-success"));
            Label uuid = Fx.label(a.uuid, "faint", "tiny", "mono");
            Label note = Fx.label(I18n.t(a.isMicrosoft() ? "accounts.msNote" : "accounts.offlineNote"), "dim", "small");
            VBox text = new VBox(3, titleRow, note, uuid);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button use = Fx.button(I18n.t("accounts.use"), Icons.CHECK);
            use.setVisible(!isActive);
            use.setManaged(!isActive);
            use.setOnAction(e -> {
                AccountStore.setActive(a);
                window.accountsChanged();
                refresh();
            });
            Button remove = Fx.iconButton(Icons.DELETE, I18n.t("accounts.remove"), "ghost", "danger");
            remove.setOnAction(e -> Modal.confirm(I18n.t("accounts.removeTitle"), I18n.t("accounts.removeText", a.name),
                    I18n.t("accounts.remove"), true, () -> {
                        AccountStore.remove(a);
                        window.accountsChanged();
                        refresh();
                    }));
            HBox row = new HBox(14, Heads.head(a, 48), text, use, remove);
            row.getStyleClass().add("row");
            if (isActive) row.setStyle("-fx-border-color: -nx-accent;");
            list.getChildren().add(row);
        }
    }

    private void addOffline() {
        Modal.prompt(I18n.t("accounts.offlineTitle"), I18n.t("accounts.offlineLabel"), "", v -> {
            if (!OFFLINE_NAME.matcher(v).matches()) return I18n.t("accounts.offlineInvalid");
            return null;
        }, v -> {
            Account a = Account.offline(v);
            AccountStore.put(a);
            window.accountsChanged();
            refresh();
            Toasts.success(I18n.t("accounts.added", a.name));
        });
    }

    private void addMicrosoft() {
        if (Settings.get().microsoftClientId == null || Settings.get().microsoftClientId.isBlank()) {
            Modal m = Modal.of(I18n.t("accounts.noClientId.title")).width(560).text(I18n.t("accounts.noClientId.text"));
            m.closeButton(I18n.t("common.close"), null, null);
            m.closeButton(I18n.t("accounts.noClientId.settings"), "primary", window::showSettings);
            m.show();
            return;
        }
        Modal wait = Modal.of(I18n.t("accounts.ms.title"));
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(32, 32);
        wait.content(Fx.row(12, pi, Fx.label(I18n.t("accounts.ms.requesting"), "dim")));
        wait.closeButton(I18n.t("common.cancel"), null, null);
        wait.show();
        Fx.async(MicrosoftAuth::startDeviceCode, code -> {
            if (!wait.isShowing()) return;
            wait.close();
            showDeviceCode(code);
        }, err -> {
            wait.close();
            Modal.message(I18n.t("accounts.ms.failed"), Fx.errorText(err));
        });
    }

    private void showDeviceCode(MicrosoftAuth.DeviceCode code) {
        AtomicBoolean cancelled = new AtomicBoolean();
        Modal m = Modal.of(I18n.t("accounts.ms.title")).width(520).closable(false);
        Label step1 = Fx.label(I18n.t("accounts.ms.step1"), "dim");
        Label link = Fx.label(code.verificationUri(), "accent-text", "h3");
        link.setStyle("-fx-cursor: hand; -fx-underline: true;");
        link.setOnMouseClicked(e -> window.openLink(code.verificationUri()));
        Label step2 = Fx.label(I18n.t("accounts.ms.step2"), "dim");
        Label codeLabel = Fx.label(code.userCode(), "device-code");
        Button copy = Fx.button(I18n.t("accounts.ms.copy"), Icons.COPY);
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(code.userCode());
            Clipboard.getSystemClipboard().setContent(c);
            Toasts.info(I18n.t("accounts.ms.copied"));
        });
        HBox codeRow = new HBox(12, codeLabel, copy);
        codeRow.setAlignment(Pos.CENTER_LEFT);
        ProgressIndicator pi = new ProgressIndicator();
        pi.setMaxSize(18, 18);
        Label status = Fx.label(I18n.t("accounts.ms.waiting"), "faint", "small");
        status.setWrapText(true);
        m.content(step1, link, step2, codeRow, Fx.row(10, pi, status));
        m.closeButton(I18n.t("common.cancel"), null, () -> cancelled.set(true));
        m.button(I18n.t("accounts.ms.open"), "primary", () -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(code.userCode());
            Clipboard.getSystemClipboard().setContent(c);
            window.openLink(code.verificationUri());
        });
        m.show();

        Fx.async(() -> MicrosoftAuth.completeDeviceCode(code, cancelled::get,
                s -> Platform.runLater(() -> status.setText(s))), account -> {
            m.close();
            if (cancelled.get()) return;
            AccountStore.put(account);
            window.accountsChanged();
            refresh();
            Toasts.success(I18n.t("accounts.added", account.name));
        }, err -> {
            m.close();
            if (cancelled.get()) return;
            Modal.message(I18n.t("accounts.ms.failed"), Fx.errorText(err));
        });
    }

    @Override
    public void onHide() { }
}
