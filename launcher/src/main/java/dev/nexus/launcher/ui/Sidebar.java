package dev.nexus.launcher.ui;

import dev.nexus.launcher.auth.Account;
import dev.nexus.launcher.auth.AccountStore;
import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.i18n.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Left navigation column: brand, page buttons and the active account. */
final class Sidebar extends VBox {
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final ToggleGroup group = new ToggleGroup();
    private final HBox accountChip = new HBox(10);

    Sidebar(MainWindow window) {
        getStyleClass().add("sidebar");
        setPrefWidth(214);
        setMinWidth(214);

        Label title = Fx.label("NEXUS", "brand-title");
        Label sub = Fx.label("LAUNCHER  " + BuildInfo.VERSION, "brand-sub");
        HBox brand = new HBox(11, MainWindow.logoNode(34), new VBox(0, title, sub));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setStyle("-fx-padding: 0 4 18 6;");

        getChildren().add(brand);
        add("instances", I18n.t("nav.instances"), Icons.GRID, window::showInstances);
        add("accounts", I18n.t("nav.accounts"), Icons.PERSON, window::showAccounts);
        add("settings", I18n.t("nav.settings"), Icons.SETTINGS, window::showSettings);
        add("about", I18n.t("nav.about"), Icons.INFO, window::showAbout);

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        accountChip.getStyleClass().add("card-flat");
        accountChip.setAlignment(Pos.CENTER_LEFT);
        accountChip.setStyle("-fx-cursor: hand; -fx-padding: 10;");
        accountChip.setOnMouseClicked(e -> window.showAccounts());
        getChildren().addAll(grow, accountChip);
        refreshAccount();
    }

    private void add(String key, String text, String icon, Runnable action) {
        ToggleButton b = new ToggleButton(text, Icons.icon(icon, 18));
        b.getStyleClass().setAll("nav-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setToggleGroup(group);
        b.setOnAction(e -> {
            action.run();
            b.setSelected(true);
        });
        buttons.put(key, b);
        getChildren().add(b);
    }

    void select(String key) {
        ToggleButton b = buttons.get(key);
        if (b != null) b.setSelected(true);
    }

    void refreshAccount() {
        Account a = AccountStore.active();
        accountChip.getChildren().clear();
        if (a == null) {
            Label l = Fx.label(I18n.t("sidebar.noAccount"), "dim", "small");
            l.setWrapText(true);
            accountChip.getChildren().addAll(Icons.icon(Icons.PERSON, 18), l);
            return;
        }
        Label name = Fx.label(a.name, "h3");
        Label type = Fx.label(I18n.t(a.isMicrosoft() ? "account.microsoft" : "account.offline"), "faint", "tiny");
        VBox text = new VBox(1, name, type);
        accountChip.getChildren().addAll(Heads.head(a, 34), text);
    }
}
