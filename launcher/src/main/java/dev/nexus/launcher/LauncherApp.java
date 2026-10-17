package dev.nexus.launcher;

import dev.nexus.launcher.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

/** JavaFX entry point. */
public final class LauncherApp extends Application {

    static void start(String[] args) {
        // Keep the JVM alive while the window is hidden during a game ("hide launcher" behaviour).
        javafx.application.Platform.setImplicitExit(false);
        Application.launch(LauncherApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        new MainWindow(stage, getHostServices()).show();
    }
}
