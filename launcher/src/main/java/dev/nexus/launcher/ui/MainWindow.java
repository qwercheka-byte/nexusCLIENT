package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.BuildInfo;
import dev.nexus.launcher.core.Log;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.game.GameProcess;
import dev.nexus.launcher.i18n.I18n;
import dev.nexus.launcher.instance.Instance;
import dev.nexus.launcher.instance.InstanceManager;
import dev.nexus.launcher.update.Updates;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The main window: sidebar, page area, play bar, plus overlay layers for dialogs and toasts. */
public final class MainWindow {
    private static MainWindow instance;

    private final Stage stage;
    private final HostServices hostServices;
    private final StackPane root = new StackPane();
    private final StackPane pageHolder = new StackPane();
    private final Sidebar sidebar;
    private final PlayBar playBar;
    private final UpdateBanner updateBanner;
    private final PhotoBackground background = new PhotoBackground();
    private Node currentPage;
    private InstancesPage instancesPage;
    private final WindowChrome chrome;
    private boolean checkingUpdates;
    private static final double MIN_W = 960, MIN_H = 620;

    public MainWindow(Stage stage, HostServices hostServices) {
        instance = this;
        this.stage = stage;
        this.hostServices = hostServices;

        sidebar = new Sidebar(this);
        playBar = new PlayBar(this);
        pageHolder.getStyleClass().add("content");
        VBox.setVgrow(pageHolder, Priority.ALWAYS);
        updateBanner = new UpdateBanner(this);
        chrome = Settings.get().customTitleBar ? new WindowChrome(stage, root, MIN_W, MIN_H) : null;
        VBox main = new VBox(updateBanner, pageHolder, playBar);
        if (chrome != null) {
            // room for the window buttons above the page; the strip itself is part of the transparent title bar
            Region titleSpace = new Region();
            titleSpace.setMinHeight(WindowChrome.HEIGHT - 6);
            main.getChildren().add(0, titleSpace);
        }
        HBox.setHgrow(main, Priority.ALWAYS);
        HBox app = new HBox(sidebar, main);

        StackPane modalLayer = new StackPane();
        modalLayer.setVisible(false);
        Modal.install(modalLayer);
        VBox toasts = Toasts.createContainer();
        StackPane.setAlignment(toasts, Pos.TOP_RIGHT);
        root.getChildren().addAll(background, app, modalLayer, toasts);
        if (chrome != null) {
            StackPane.setMargin(toasts, new javafx.geometry.Insets(WindowChrome.HEIGHT - 8, 0, 0, 0));
            root.getChildren().add(chrome.bar()); // above the modal layer: the window can be moved/closed while a dialog is open
        }
        Theme.apply(root, Settings.get().accent);
        applyBackground();
    }

    /** Applies the photo background settings (also called from the settings page). */
    public void applyBackground() {
        background.reload();
        if (background.active()) {
            if (!root.getStyleClass().contains("photo-bg")) root.getStyleClass().add("photo-bg");
        } else {
            root.getStyleClass().remove("photo-bg");
        }
    }

    public static MainWindow get() {
        return instance;
    }

    public Stage stage() {
        return stage;
    }

    public void show() {
        Settings s = Settings.get();
        Scene scene = new Scene(root, Math.max(960, s.windowWidth), Math.max(620, s.windowHeight), Color.web("#0d0e13"));
        scene.getStylesheets().add(MainWindow.class.getResource("/dev/nexus/launcher/ui/nexus.css").toExternalForm());
        if (chrome != null) {
            stage.initStyle(StageStyle.UNDECORATED);
            chrome.attach(scene);
        }
        stage.setScene(scene);
        stage.setTitle(BuildInfo.NAME);
        stage.setMinWidth(MIN_W);
        stage.setMinHeight(MIN_H);
        if (chrome == null) stage.setMaximized(s.windowMaximized);
        stage.getIcons().addAll(appIcons());
        stage.setOnCloseRequest(e -> {
            e.consume();
            requestExit();
        });
        background.attach(stage);
        stage.show();
        if (chrome != null && s.windowMaximized) chrome.maximize();
        // license gate first: pages and the play bar stay inaccessible until granted
        LicenseGate.require(root, () -> {
            showHome();
            afterStart();
        });
    }

    // ---- updates ----

    private void afterStart() {
        Settings s = Settings.get();
        if (!s.lastVersion.isEmpty() && !s.lastVersion.equals(BuildInfo.VERSION)) {
            Toasts.success(I18n.t("update.done", BuildInfo.VERSION));
        }
        if (!s.lastVersion.equals(BuildInfo.VERSION)) {
            s.lastVersion = BuildInfo.VERSION;
            Settings.save();
        }
        Fx.async(() -> {
            Updates.cleanup();
            Updates.refreshWindowsStarter();
            return true;
        }, x -> { }, err -> { });
        // first check shortly after start, then every 6 hours (the launcher often stays open, hidden, during play)
        Timeline auto = new Timeline(new KeyFrame(Duration.seconds(2.5), e -> autoCheck()),
                new KeyFrame(Duration.hours(6)));
        auto.setCycleCount(Animation.INDEFINITE);
        auto.play();
    }

    private void autoCheck() {
        if (Settings.get().checkUpdates && Updates.configured()) checkForUpdates(false, null);
    }

    /**
     * Checks for updates and shows the banner when something is available. {@code manual} reports errors and
     * "up to date" as toasts; automatic checks only log them.
     */
    public void checkForUpdates(boolean manual, Consumer<Updates.Check> done) {
        if (checkingUpdates) {
            if (done != null) done.accept(null);
            return;
        }
        checkingUpdates = true;
        Fx.async(Updates::check, c -> {
            checkingUpdates = false;
            // An automatic check that was disabled while the request was in flight must not install anything.
            if (manual || Settings.get().checkUpdates) {
                boolean autoInstall = Settings.get().checkUpdates && Updates.safeForAutomaticInstall(c);
                if (c.any() && !autoInstall && Settings.get().checkUpdates) Log.warn("Automatic update skipped: HTTPS is required");
                updateBanner.offer(c, autoInstall);
            }
            if (manual && !c.any()) Toasts.success(I18n.t("update.upToDate", BuildInfo.VERSION));
            if (done != null) done.accept(c);
        }, err -> {
            checkingUpdates = false;
            Log.warn("Update check failed: " + Fx.errorText(err));
            if (manual) Toasts.error(I18n.t("update.checkFailed", Fx.errorText(err)));
            if (done != null) done.accept(null);
        });
    }

    /** Exits so that the update helper can replace the jar; it starts the new version afterwards. */
    void exitForUpdate() {
        saveWindowState();
        Settings.saveNow();
        Log.info("Exiting to install a launcher update");
        Platform.exit();
        System.exit(0);
    }

    /** Exits the launcher, asking first when games started from it are still running. */
    public void requestExit() {
        int running = GameProcess.runningCount();
        if (running == 0) {
            exit();
            return;
        }
        Modal.confirm(I18n.t("exit.title"), I18n.t("exit.running", running), I18n.t("exit.confirm"), false, this::exit);
    }

    private void exit() {
        saveWindowState();
        Settings.saveNow(); // save() is asynchronous and would race with System.exit (window size was not remembered)
        Log.info("Launcher closed");
        Platform.exit();
        System.exit(0);
    }

    public void saveWindowState() {
        Settings s = Settings.get();
        if (stage.isShowing() && chrome != null) {
            s.windowMaximized = chrome.isMaximized();
            s.windowWidth = chrome.normalBounds().getWidth();
            s.windowHeight = chrome.normalBounds().getHeight();
        } else if (stage.isShowing()) {
            s.windowMaximized = stage.isMaximized();
            if (!stage.isMaximized()) {
                s.windowWidth = stage.getScene().getWidth();
                s.windowHeight = stage.getScene().getHeight();
            }
        }
        Settings.save();
    }

    public void applyAccent(String accent) {
        Theme.apply(root, accent);
    }

    public void openLink(String url) {
        try {
            hostServices.showDocument(url);
        } catch (Exception e) {
            Toasts.error(I18n.t("error.openLink", url));
        }
    }

    // ---- navigation ----

    private void setPage(Node page, String nav) {
        if (currentPage instanceof Page p) p.onHide();
        currentPage = page;
        pageHolder.getChildren().setAll(page);
        boolean home = "home".equals(nav);
        sidebar.setVisible(!home);
        sidebar.setManaged(!home);
        playBar.setVisible(!home);
        playBar.setManaged(!home);
        sidebar.select(nav);
        page.setOpacity(0);
        FadeTransition f = new FadeTransition(Duration.millis(140), page);
        f.setToValue(1);
        f.play();
    }

    public void showHome() {
        HomePage home = new HomePage(this);
        setPage(home, "home");
        home.onShow();
    }

    public void showInstances() {
        if (instancesPage == null) instancesPage = new InstancesPage(this);
        instancesPage.refresh();
        setPage(instancesPage, "instances");
    }

    public void showInstance(Instance inst, String tab) {
        setPage(new InstancePage(this, inst, tab), "instances");
    }

    public void showAccounts() {
        setPage(new AccountsPage(this), "accounts");
    }

    public void showSettings() {
        setPage(new SettingsPage(this), "settings");
    }

    public void showPacks() {
        setPage(new ResourcePacksPage(this), "packs");
    }

    public void showAssistant() {
        setPage(new AssistantPage(this), "assistant");
    }

    public void showAbout() {
        setPage(new AboutPage(this), "about");
    }

    // ---- selection & shared state ----

    public Instance selectedInstance() {
        Instance i = InstanceManager.find(Settings.get().selectedInstance);
        if (i == null && !InstanceManager.all().isEmpty()) i = InstanceManager.all().get(0);
        return i;
    }

    public void select(Instance inst) {
        Settings.get().selectedInstance = inst == null ? "" : inst.id;
        Settings.save();
        playBar.refresh();
        if (instancesPage != null) instancesPage.updateSelection();
    }

    /** Called when instances were created/renamed/deleted. */
    public void instancesChanged() {
        if (instancesPage != null) instancesPage.refresh();
        playBar.refresh();
    }

    public void accountsChanged() {
        sidebar.refreshAccount();
    }

    /** Pages that need to release resources (listeners, timers) when they are left. */
    public interface Page {
        void onHide();
    }

    // ---- icon ----

    private List<Image> appIcons() {
        List<Image> out = new ArrayList<>();
        try {
            String url = MainWindow.class.getResource("/dev/nexus/launcher/img/app-icon.png").toExternalForm();
            for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
                Image icon = new Image(url, size, size, true, true);
                if (icon.isError()) throw new IllegalStateException("Cannot decode application icon", icon.getException());
                out.add(icon);
            }
        } catch (Exception e) {
            Log.warn("Cannot load the window icon: " + e.getMessage());
        }
        return out;
    }

    /** The fingerprint on a transparent background, cropped closer for legibility at UI sizes. */
    static Node logoNode(double size) {
        Image image = new Image(MainWindow.class.getResource("/dev/nexus/launcher/img/brand-fingerprint.png").toExternalForm());
        ImageView view = new ImageView(image);
        double side = Math.min(image.getWidth(), image.getHeight()) * 0.73;
        view.setViewport(new Rectangle2D((image.getWidth() - side) / 2, (image.getHeight() - side) / 2, side, side));
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
