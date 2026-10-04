package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.util.LogManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * JavaFX entry point. The UI only talks to {@link AppContext}, which wires the backend services.
 */
public final class VideoDownloaderApplication extends Application {
    private static final Logger LOGGER = LogManager.getLogger(VideoDownloaderApplication.class);
    private static final String ICON_RESOURCE = "/icon.png";

    private AppContext context;
    private MainView mainView;

    @Override
    public void start(Stage stage) {
        applyIcon(stage);
        // A placeholder scene lets the environment warning dialog own the stage (and inherit the window
        // icon) before the real interface is built.
        stage.setScene(new Scene(new StackPane()));
        context = new AppContext();
        EnvironmentDialog.showWarnings(stage, context.config(), context.environmentStatus());
        mainView = new MainView(stage, context);

        stage.setTitle("Video Downloader");
        stage.setScene(new Scene(mainView.node(), 1000, 700));
        stage.setOnHidden(event -> closeResources());
        stage.show();
    }

    private static void applyIcon(Stage stage) {
        try (InputStream icon = VideoDownloaderApplication.class.getResourceAsStream(ICON_RESOURCE)) {
            if (icon != null) {
                stage.getIcons().add(new Image(icon));
            }
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn("应用图标无法加载，将使用系统默认图标。");
        }
    }

    private void closeResources() {
        if (mainView != null) {
            mainView.stop();
        }
        if (context != null) {
            context.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
