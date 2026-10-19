package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.license.Hwid;
import dev.nexus.launcher.license.LicenseApi;
import dev.nexus.launcher.license.LicenseApi.LicenseApiException;
import dev.nexus.launcher.license.LicenseStore;
import dev.nexus.launcher.license.Verifier;
import javafx.animation.Timeline;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The license gate rendered on top of the whole window before any launcher page is usable.
 * Three flows: sign-in, registration (+ immediate key activation), and key activation for an
 * existing account. Everything goes through {@link LicenseApi}; on success the snapshot is cached
 * locally (HWID-encrypted) and a background vigil keeps re-verifying it while the launcher runs.
 */
public final class LicenseGate {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault());

    private final StackPane root;
    private final StackPane backdrop = new StackPane();
    private final TextField login = new TextField();
    private final PasswordField password = new PasswordField();
    private final PasswordField password2 = new PasswordField();
    private final TextField keyField = new TextField();
    private final Label error = new Label();
    private final Label info = new Label();
    private final Runnable onGranted;
    private ToggleButton tabLogin, tabReg, tabKey;

    private LicenseGate(StackPane root, Runnable onGranted) {
        this.root = root;
        this.onGranted = onGranted;
    }

    /** Decides: grant silently (cached license), grant offline, or show the gate. */
    public static void require(StackPane root, Runnable onGranted) {
        LicenseGate gate = new LicenseGate(root, onGranted);
        gate.boot();
    }

    // --------------------------------------------------------------- boot flow

    private void boot() {
        LicenseStore.Snapshot snap = LicenseStore.load();
        if (snap == null) {
            show("");
        } else {
            // Try an online check first; fall back to the offline grace window if the server is unreachable.
            show(I18n.t("lic.checking"));
            Fx.async(() -> LicenseApi.verify(snap.token, Hwid.get()), grant -> {
                if (!grant.frozen() && grant.paidUntil() > System.currentTimeMillis()) {
                    snap.lastOnlineVerifyMs = System.currentTimeMillis();
                    snap.paidUntilMs = grant.paidUntil();
                    saveAndGrant(snap);
                } else {
                    error.setText(licenseError(grant.frozen() ? "FROZEN" : "EXPIRED", grant.paidUntil()));
                }
            }, err -> {
                if (err instanceof LicenseApiException e) {
                    LicenseStore.clear();
                    error.setText(licenseError(e.code, e.paidUntil));
                } else if (System.currentTimeMillis() < snap.offlineDeadline()) {
                    Log.info("license: offline mode, window ends " + DATE.format(Instant.ofEpochMilli(snap.offlineDeadline())));
                    Toasts.info(I18n.t("lic.offlineToast", DATE.format(Instant.ofEpochMilli(snap.offlineDeadline()))));
                    grant();
                } else {
                    error.setText(I18n.t("lic.err.networkExpired", DATE.format(Instant.ofEpochMilli(snap.offlineDeadline()))));
                }
            });
        }
    }

    // --------------------------------------------------------------- UI

    private void show(String banner) {
        backdrop.getStyleClass().add("modal-backdrop");
        backdrop.setStyle("-fx-background-color: rgba(5, 6, 10, 0.88);");

        Label brand = Fx.label("N E X U S   C L I E N T", "settings-note");
        brand.setStyle("-fx-letter-spacing: 4px; -fx-text-fill: -nx-text-faint;");
        Label h1 = Fx.label(I18n.t("lic.greeting"), "h1");
        h1.setStyle("-fx-font-size: 30px;");
        Label sub = Fx.label(I18n.t("lic.subtitle"), "settings-note");
        sub.setWrapText(true);

        ToggleGroup tabs = new ToggleGroup();
        tabLogin = seg(tabs, I18n.t("lic.tab.login"));
        tabReg = seg(tabs, I18n.t("lic.tab.register"));
        tabKey = seg(tabs, I18n.t("lic.tab.key"));
        tabLogin.setSelected(true);
        HBox seg = new HBox(tabLogin, tabReg, tabKey);
        seg.getStyleClass().add("segment");
        seg.setAlignment(Pos.CENTER);
        HBox.setHgrow(tabLogin, Priority.ALWAYS);
        HBox.setHgrow(tabReg, Priority.ALWAYS);
        HBox.setHgrow(tabKey, Priority.ALWAYS);

        login.setPromptText(I18n.t("lic.login"));
        password.setPromptText(I18n.t("lic.password"));
        password2.setPromptText(I18n.t("lic.password2"));
        keyField.setPromptText("NEXUS-XXXX-XXXX-XXXX");
        for (TextField f : new TextField[]{login, password, password2, keyField}) {
            f.setPrefHeight(40);
            f.setStyle("-fx-font-size: 14px;");
        }

        Label loginCap = Fx.label(I18n.t("lic.loginCap"), "settings-note");
        Label keyCap = Fx.label(I18n.t("lic.keyCap"), "settings-note");
        keyCap.setWrapText(true);

        javafx.scene.control.Button go = Fx.button(I18n.t("lic.go.login"), null, "primary");
        go.setPrefHeight(42);
        go.setMaxWidth(Double.MAX_VALUE);
        go.setDefaultButton(true);

        error.getStyleClass().add("status-error");
        error.setStyle("-fx-text-fill: -nx-danger;");
        error.setWrapText(true);
        error.setText(banner == null ? "" : banner);
        error.setManaged(false);
        error.setVisible(false);
        info.getStyleClass().add("settings-note");
        info.setWrapText(true);
        info.setManaged(false);
        info.setVisible(false);

        ProgressIndicator spin = new ProgressIndicator();
        spin.setPrefSize(22, 22);
        spin.setVisible(false);
        spin.setManaged(false);

        VBox card = new VBox(12, brand, h1, sub, seg, loginCap, login, password, keyCap, password2, keyField, info, error, go, spin);
        card.getStyleClass().add("dialog");
        card.setPadding(new Insets(30, 34, 30, 34));
        card.setMaxSize(420, Region.USE_PREF_SIZE);
        card.setAlignment(Pos.CENTER_LEFT);

        Runnable relayout = () -> {
            boolean register = tabReg.isSelected(), withKey = tabKey.isSelected();
            loginCap.setManaged(register);
            loginCap.setVisible(register);
            password2.setManaged(register);
            password2.setVisible(register);
            keyCap.setManaged(withKey);
            keyCap.setVisible(withKey);
            keyField.setManaged(withKey);
            keyField.setVisible(withKey);
            info.setManaged(false);
            info.setVisible(false);
            if (withKey && !login.getText().isBlank()) keyField.requestFocus();
            go.setText(withKey ? I18n.t("lic.go.key") : register ? I18n.t("lic.go.register") : I18n.t("lic.go.login"));
        };
        tabLogin.setOnAction(e -> relayout.run());
        tabReg.setOnAction(e -> relayout.run());
        tabKey.setOnAction(e -> relayout.run());
        relayout.run();

        go.setOnAction(e -> {
            error.setManaged(false);
            error.setVisible(false);
            boolean register = tabReg.isSelected(), withKey = tabKey.isSelected();
            String l = login.getText().trim();
            char[] p = password.getText().toCharArray();
            if (!l.matches("[A-Za-z0-9_]{3,16}")) { showError(I18n.t("lic.err.badLogin")); return; }
            if (password.getText().length() < 6) { showError(I18n.t("lic.err.badPass")); return; }
            if (register && !password.getText().equals(password2.getText())) { showError(I18n.t("lic.err.passMatch")); return; }
            String code = keyField.getText().trim();
            if (withKey && !code.toUpperCase().matches("NEXUS(-[2-9A-HJ-NP-Z]{4}){3}")) { showError(I18n.t("lic.err.badKey")); return; }

            go.setDisable(true);
            spin.setVisible(true);
            spin.setManaged(true);
            spin.setPrefSize(22, 22);
            spin.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            backdrop.setCursor(Cursor.WAIT);
            card.requestFocus();

            final String fLogin = l;
            final String fCode = code;
            final boolean fRegister = register, fWithKey = withKey;
            Fx.async(() -> {
                String verifier = Verifier.of(fLogin, p);
                if (fRegister) {
                    LicenseApi.register(fLogin, verifier);
                    return null; // account created; the key comes next, on the Key tab
                }
                if (fWithKey) LicenseApi.activate(fLogin, verifier, fCode);
                return LicenseApi.auth(fLogin, verifier, Hwid.get());
            }, grantRes -> {
                if (grantRes == null) {
                    go.setDisable(false);
                    spin.setVisible(false);
                    spin.setManaged(false);
                    backdrop.setCursor(Cursor.DEFAULT);
                    keyField.clear();
                    tabKey.setSelected(true);
                    relayout.run();
                    info.setText(I18n.t("lic.registered"));
                    info.setManaged(true);
                    info.setVisible(true);
                    return;
                }
                LicenseStore.Snapshot snap = new LicenseStore.Snapshot();
                snap.login = fLogin;
                snap.token = grantRes.token();
                snap.lastOnlineVerifyMs = System.currentTimeMillis();
                snap.paidUntilMs = grantRes.paidUntil();
                saveAndGrant(snap);
            }, err -> {
                go.setDisable(false);
                spin.setVisible(false);
                spin.setManaged(false);
                backdrop.setCursor(Cursor.DEFAULT);
                if (err instanceof LicenseApiException ex) {
                    if (fRegister && ex.code.equals("LOGIN_TAKEN")) {
                        // the account exists already — send the user to sign-in / key tab
                        showError(licenseError(ex.code, null));
                        tabLogin.setSelected(true);
                        relayout.run();
                    } else showError(licenseError(ex.code, ex.paidUntil));
                } else showError(I18n.t("lic.err.network"));
            });
        });

        Label note = Fx.label(I18n.t("lic.hwidNote"), "settings-note");
        note.setStyle("-fx-font-size: 11.5px;");
        note.setWrapText(true);
        note.setMaxWidth(card.getMaxWidth() - 68);
        VBox wrap = new VBox(10, card, note);
        wrap.setAlignment(Pos.CENTER);
        wrap.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        backdrop.getChildren().add(wrap);
        root.getChildren().add(backdrop);
        backdrop.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(220), backdrop);
        ft.setToValue(1);
        ft.play();
        login.requestFocus();
    }

    private ToggleButton seg(ToggleGroup g, String text) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(g);
        b.getStyleClass().add("segment-button");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    private void showError(String msg) {
        error.setText(msg);
        error.setManaged(true);
        error.setVisible(true);
    }

    private String licenseError(String code, Long paidUntil) {
        return switch (code == null ? "SERVER" : code) {
            case "LOGIN_TAKEN" -> I18n.t("lic.err.LOGIN_TAKEN");
            case "BAD_PASSWORD" -> I18n.t("lic.err.BAD_PASSWORD");
            case "BAD_CODE" -> I18n.t("lic.err.BAD_CODE");
            case "NO_ACCOUNT" -> I18n.t("lic.err.NO_ACCOUNT");
            case "FROZEN" -> I18n.t("lic.err.FROZEN");
            case "EXPIRED" -> I18n.t("lic.err.EXPIRED", paidUntil != null && paidUntil > 0
                    ? DATE.format(Instant.ofEpochMilli(paidUntil)) : "—");
            case "HWID_MISMATCH" -> I18n.t("lic.err.HWID_MISMATCH");
            case "NO_SESSION" -> I18n.t("lic.err.NO_SESSION");
            case "RLIMIT" -> I18n.t("lic.err.RLIMIT");
            default -> I18n.t("lic.err.SERVER");
        };
    }

    private void saveAndGrant(LicenseStore.Snapshot snap) {
        LicenseStore.save(snap);
        FadeTransition ft = new FadeTransition(Duration.millis(260), backdrop);
        ft.setToValue(0);
        ft.setOnFinished(e -> root.getChildren().remove(backdrop));
        ft.play();
        startVigil();
        onGranted.run();
    }

    private void grant() {
        if (backdrop.getParent() == root) {
            FadeTransition ft = new FadeTransition(Duration.millis(260), backdrop);
            ft.setToValue(0);
            ft.setOnFinished(e -> root.getChildren().remove(backdrop));
            ft.play();
        }
        startVigil();
        onGranted.run();
    }

    /** Re-verifies the cached license every hour while the launcher runs; a hard refusal re-opens the gate. */
    private void startVigil() {
        Timeline t = new Timeline(new KeyFrame(Duration.hours(1), ev -> recheck()));
        t.setCycleCount(Timeline.INDEFINITE);
        t.play();
    }

    private void recheck() {
        LicenseStore.Snapshot snap = LicenseStore.load();
        if (snap == null || !snap.needsReverify()) return;
        Fx.async(() -> LicenseApi.verify(snap.token, Hwid.get()), grantRes -> {
            if (grantRes.frozen() || grantRes.paidUntil() <= System.currentTimeMillis()) {
                LicenseStore.clear();
                Platform.runLater(() -> new LicenseGate(root, onGranted).show(licenseError(grantRes.frozen() ? "FROZEN" : "EXPIRED", grantRes.paidUntil())));
            } else {
                snap.lastOnlineVerifyMs = System.currentTimeMillis();
                snap.paidUntilMs = grantRes.paidUntil();
                LicenseStore.save(snap);
            }
        }, err -> {
            if (err instanceof LicenseApiException ex) {
                LicenseStore.clear();
                Platform.runLater(() -> new LicenseGate(root, onGranted).show(licenseError(ex.code, ex.paidUntil)));
            }
            // network trouble stays silent: the offline grace window covers it
        });
    }
}
