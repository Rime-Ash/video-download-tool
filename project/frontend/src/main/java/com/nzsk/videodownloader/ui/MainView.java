package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.model.EnvironmentStatus;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Main window: a download tab, a settings tab and an environment status bar.
 */
final class MainView {
    private final BorderPane root = new BorderPane();
    private final Label environmentLabel = new Label();
    private final AppContext context;
    private final DownloadPane downloadPane;

    MainView(Stage stage, AppContext context) {
        Objects.requireNonNull(stage, "stage");
        this.context = Objects.requireNonNull(context, "context");
        downloadPane = new DownloadPane(stage, context);
        SettingsPane settingsPane = new SettingsPane(stage, context, this::refreshEnvironment);

        Tab downloadTab = new Tab("下载", downloadPane.node());
        Tab settingsTab = new Tab("设置", settingsPane.node());
        TabPane tabs = new TabPane(downloadTab, settingsTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> {
                    if (current == settingsTab) {
                        refreshEnvironment();
                    }
                });

        environmentLabel.setPadding(new Insets(6, 14, 8, 14));
        environmentLabel.setWrapText(true);
        root.setCenter(tabs);
        root.setBottom(environmentLabel);
        refreshEnvironment();
    }

    Parent node() {
        return root;
    }

    void stop() {
        downloadPane.stop();
    }

    private void refreshEnvironment() {
        EnvironmentStatus status = context.environmentStatus();
        environmentLabel.setText("yt-dlp：" + availability(status.ytDlpAvailable())
                + "　FFmpeg：" + availability(status.ffmpegAvailable())
                + "　下载目录：" + (status.downloadDirectoryWritable() ? "可写入" : "不可写入")
                + "　剩余空间：" + UiFormatters.freeSpace(status.usableSpace()));
        downloadPane.refreshConfiguration();
    }

    private static String availability(boolean available) {
        return available ? "已就绪" : "未找到";
    }
}
