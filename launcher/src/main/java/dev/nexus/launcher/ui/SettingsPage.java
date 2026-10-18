package dev.nexus.launcher.ui;

import dev.nexus.launcher.core.FileUtil;
import dev.nexus.launcher.core.LauncherDirs;
import dev.nexus.launcher.core.Settings;
import dev.nexus.launcher.i18n.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static dev.nexus.launcher.ui.InstanceSettingsTab.card;
import static dev.nexus.launcher.ui.InstanceSettingsTab.field;
import static dev.nexus.launcher.ui.InstanceSettingsTab.hint;

/** Global launcher settings. Every change is saved immediately. */
final class SettingsPage extends VBox implements MainWindow.Page {
    private final MainWindow window;
    private final InstanceSettingsTab.MemoryControl memory;
    private final TextField jvm = new TextField();
    private final TextField javaField = new TextField();
    private final TextField clientId = new TextField();
    private final Label cacheSize = Fx.label("…", "dim");

    SettingsPage(MainWindow window) {
        this.window = window;
        getStyleClass().add("page");
        Settings s = Settings.get();

        // --- interface ---
        ComboBox<String> lang = new ComboBox<>();
        lang.getItems().setAll("ru", "en");
        lang.setValue(s.language);
        lang.setCellFactory(v -> langCell());
        lang.setButtonCell(langCell());
        lang.valueProperty().addListener((o, a, b) -> {
            s.language = b;
            Settings.save();
            Toasts.info(I18n.t("settings.restartLanguage"));
        });
        HBox accents = new HBox(10);
        for (String id : Theme.ACCENTS.keySet()) {
            Region dot = new Region();
            dot.getStyleClass().add("color-dot");
            dot.setStyle("-fx-background-color: linear-gradient(to bottom right, " + Theme.ACCENTS.get(id)[0] + ", " + Theme.ACCENTS.get(id)[1] + ");");
            dot.setUserData(id);
            dot.setOnMouseClicked(e -> {
                s.accent = id;
                Settings.save();
                window.applyAccent(id);
                accents.getChildren().forEach(n -> n.getStyleClass().remove("selected"));
                dot.getStyleClass().add("selected");
            });
            if (id.equals(s.accent)) dot.getStyleClass().add("selected");
            accents.getChildren().add(dot);
        }

        // --- game ---
        memory = new InstanceSettingsTab.MemoryControl(s.minMemoryMb, s.maxMemoryMb);
        Button saveMem = Fx.button(I18n.t("common.apply"), Icons.CHECK);
        saveMem.setOnAction(e -> {
            s.minMemoryMb = memory.min();
            s.maxMemoryMb = memory.max();
            Settings.save();
            Toasts.success(I18n.t("settings.saved"));
        });
        jvm.setText(s.jvmArgs);
        HBox.setHgrow(jvm, Priority.ALWAYS);
        Button jvmReset = Fx.iconButton(Icons.REFRESH, I18n.t("settings.jvmReset"));
        jvmReset.setOnAction(e -> jvm.setText(Settings.DEFAULT_JVM_ARGS));
        jvm.textProperty().addListener((o, a, b) -> {
            s.jvmArgs = b.trim();
            Settings.save();
        });
        javaField.setText(s.javaPath);
        javaField.setPromptText(I18n.t("settings.javaAuto"));
        HBox.setHgrow(javaField, Priority.ALWAYS);
        javaField.textProperty().addListener((o, a, b) -> {
            s.javaPath = b.trim();
            Settings.save();
        });
        Button browse = Fx.button(I18n.t("common.browse"), Icons.FOLDER);
        browse.setOnAction(e -> {
            File f = InstanceSettingsTab.chooseJava(window);
            if (f != null) javaField.setText(f.getAbsolutePath());
        });

        ComboBox<Settings.LaunchBehavior> behavior = new ComboBox<>();
        behavior.getItems().setAll(Settings.LaunchBehavior.values());
        behavior.setValue(s.launchBehavior);
        behavior.setPrefWidth(360);
        behavior.setCellFactory(v -> behaviorCell());
        behavior.setButtonCell(behaviorCell());
        behavior.valueProperty().addListener((o, a, b) -> {
            s.launchBehavior = b;
            Settings.save();
        });
        CheckBox console = check(I18n.t("settings.openConsole"), s.openConsoleOnLaunch, v -> s.openConsoleOnLaunch = v);

        // --- versions & downloads ---
        CheckBox snapshots = check(I18n.t("settings.showSnapshots"), s.showSnapshots, v -> s.showSnapshots = v);
        CheckBox old = check(I18n.t("settings.showOld"), s.showOldVersions, v -> s.showOldVersions = v);
        Spinner<Integer> threads = new Spinner<>(1, 32, s.maxDownloads);
        threads.setPrefWidth(90);
        threads.valueProperty().addListener((o, a, b) -> {
            s.maxDownloads = b;
            Settings.save();
        });

        // --- microsoft ---
        clientId.setText(s.microsoftClientId);
        clientId.setPromptText("00000000-0000-0000-0000-000000000000");
        HBox.setHgrow(clientId, Priority.ALWAYS);
        clientId.textProperty().addListener((o, a, b) -> {
            s.microsoftClientId = b.trim();
            Settings.save();
        });
        Label msHelp = hint(I18n.t("settings.msHelp"));
        Button azure = Fx.button(I18n.t("settings.msAzure"), Icons.OPEN, "ghost");
        azure.setOnAction(e -> window.openLink("https://portal.azure.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade"));
        Button review = Fx.button(I18n.t("settings.msReview"), Icons.OPEN, "ghost");
        review.setOnAction(e -> window.openLink("https://aka.ms/mce-reviewappid"));

        // --- updates ---
        CheckBox autoUpdates = check(I18n.t("settings.checkUpdates"), s.checkUpdates, v -> s.checkUpdates = v);
        String clientVersion = dev.nexus.launcher.update.NexusClientStore.latestKnownVersion();
        Label versions = Fx.label(I18n.t("settings.updateVersions", dev.nexus.launcher.core.BuildInfo.VERSION, clientVersion), "dim");
        TextField updateUrl = new TextField(s.updateUrl);
        String builtIn = dev.nexus.launcher.update.UpdateManifest.builtInAddress();
        updateUrl.setPromptText(builtIn != null ? builtIn : I18n.t("settings.updateUrlNone"));
        HBox.setHgrow(updateUrl, Priority.ALWAYS);
        Button checkNow = Fx.button(I18n.t("settings.checkNow"), Icons.REFRESH);
        Label updateHint = hint(I18n.t("settings.updatesNotConfigured"));
        Runnable refreshUpdateUi = () -> {
            boolean ok = dev.nexus.launcher.update.Updates.configured();
            checkNow.setDisable(!ok);
            updateHint.setVisible(!ok);
            updateHint.setManaged(!ok);
        };
        updateUrl.textProperty().addListener((o, a, b) -> {
            s.updateUrl = b.trim();
            Settings.save();
            refreshUpdateUi.run();
        });
        checkNow.setOnAction(e -> {
            checkNow.setDisable(true);
            window.checkForUpdates(true, c -> refreshUpdateUi.run());
        });
        refreshUpdateUi.run();

        // --- storage ---
        Label dir = Fx.label(LauncherDirs.root().toString(), "mono", "dim");
        dir.setWrapText(true);
        Button openDir = Fx.button(I18n.t("settings.openFolder"), Icons.FOLDER);
        openDir.setOnAction(e -> Fx.openPath(LauncherDirs.root()));
        Button openLogs = Fx.button(I18n.t("settings.openLogs"), Icons.TERMINAL);
        openLogs.setOnAction(e -> Fx.openPath(LauncherDirs.logs()));
        Button clearCache = Fx.button(I18n.t("settings.clearCache"), Icons.DELETE, "danger");
        clearCache.setOnAction(e -> Modal.confirm(I18n.t("settings.clearCache"), I18n.t("settings.clearCacheText"), I18n.t("settings.clearCache"), true,
                () -> Fx.async(() -> {
                    FileUtil.deleteRecursively(LauncherDirs.cache());
                    Files.createDirectories(LauncherDirs.cache());
                    return true;
                }, x -> {
                    Toasts.success(I18n.t("settings.cacheCleared"));
                    updateCacheSize();
                }, err -> Toasts.error(Fx.errorText(err)))));

        // --- background ---
        CheckBox photos = check(I18n.t("settings.photoBg"), s.photoBackground, v -> {
            s.photoBackground = v;
            window.applyBackground();
        });
        CheckBox shuffle = check(I18n.t("settings.photoShuffle"), s.photoShuffle, v -> s.photoShuffle = v);
        CheckBox frame = check(I18n.t("settings.customFrame"), s.customTitleBar, v -> {
            s.customTitleBar = v;
            Toasts.info(I18n.t("settings.customFrameRestart"));
        });
        Slider dimSlider = new Slider(0, 0.9, s.photoDim);
        dimSlider.setPrefWidth(260);
        Label dimValue = Fx.label(Math.round(s.photoDim * 100) + " %", "dim");
        dimSlider.valueProperty().addListener((o, a, b) -> {
            s.photoDim = Math.round(b.doubleValue() * 20) / 20.0;
            dimValue.setText(Math.round(s.photoDim * 100) + " %");
            Settings.save();
            window.applyBackground();
        });
        Button photoFolder = Fx.button(I18n.t("settings.photoFolder"), Icons.FOLDER);
        photoFolder.setOnAction(e -> Fx.openPath(PhotoBackground.folder()));
        Button photoReload = Fx.button(I18n.t("settings.photoReload"), Icons.REFRESH);
        photoReload.setOnAction(e -> window.applyBackground());

        VBox content = new VBox(14,
                card(I18n.t("settings.interface"),
                        field(I18n.t("settings.language"), lang),
                        field(I18n.t("settings.accent"), accents),
                        frame, hint(I18n.t("settings.customFrameHint"))),
                card(I18n.t("settings.background"), photos, shuffle,
                        field(I18n.t("settings.photoDim"), Fx.row(10, dimSlider, dimValue)),
                        hint(I18n.t("settings.photoHint")),
                        Fx.row(8, photoFolder, photoReload)),
                card(I18n.t("settings.game"),
                        Fx.label(I18n.t("settings.memory"), "dim", "small"), memory, Fx.row(0, Fx.spacer(), saveMem),
                        field(I18n.t("settings.jvmArgs"), Fx.row(8, jvm, jvmReset)),
                        field(I18n.t("settings.java"), Fx.row(8, javaField, browse)),
                        hint(I18n.t("settings.javaHint")),
                        field(I18n.t("settings.behavior"), behavior),
                        console,
                        hint(I18n.t("settings.instanceOverrides"))),
                card(I18n.t("settings.versions"), snapshots, old,
                        Fx.row(10, Fx.label(I18n.t("settings.threads"), "dim"), threads)),
                card(I18n.t("settings.microsoft"),
                        field(I18n.t("settings.clientId"), clientId), msHelp, Fx.row(8, azure, review)),
                card(I18n.t("settings.updates"), versions, autoUpdates,
                        field(I18n.t("settings.updateUrl"), updateUrl), updateHint, Fx.row(8, checkNow)),
                card(I18n.t("settings.storage"),
                        field(I18n.t("settings.dataDir"), dir),
                        Fx.row(10, Fx.label(I18n.t("settings.cache"), "dim"), cacheSize),
                        Fx.row(8, openDir, openLogs, Fx.spacer(), clearCache)));
        content.setStyle("-fx-padding: 0 10 10 0;");
        content.setMaxWidth(860);
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);
        HBox header = new HBox(Fx.label(I18n.t("settings.title"), "h1"));
        header.getStyleClass().add("page-header");
        getChildren().addAll(header, sp);
        updateCacheSize();
    }

    private void updateCacheSize() {
        Fx.async(() -> dirSize(LauncherDirs.cache()), size -> cacheSize.setText(InstanceSettingsTab.MemoryControl.fmt(size / 1024.0 / 1024.0)), err -> cacheSize.setText("?"));
    }

    private static long dirSize(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        }
    }

    private static CheckBox check(String text, boolean value, java.util.function.Consumer<Boolean> setter) {
        CheckBox c = new CheckBox(text);
        c.setSelected(value);
        c.selectedProperty().addListener((o, a, b) -> {
            setter.accept(b);
            Settings.save();
        });
        return c;
    }

    private static ListCell<String> langCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.equals("ru") ? "Русский" : "English");
            }
        };
    }

    private static ListCell<Settings.LaunchBehavior> behaviorCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Settings.LaunchBehavior b, boolean empty) {
                super.updateItem(b, empty);
                setText(empty || b == null ? null : I18n.t("settings.behavior." + b.name().toLowerCase(java.util.Locale.ROOT)));
            }
        };
    }

    @Override
    public void onHide() {
        Settings.save();
    }
}
