package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.model.AppConfig;
import com.nzsk.videodownloader.model.EnvironmentStatus;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows actionable, non-blocking warnings when a required tool or directory is missing.
 */
final class EnvironmentDialog {
    private static final long MINIMUM_FREE_SPACE_BYTES = 1024L * 1024L * 1024L;

    private EnvironmentDialog() {
    }

    static void showWarnings(Window owner, AppConfig config, EnvironmentStatus status) {
        List<String> warnings = warnings(config, status);
        if (warnings.isEmpty()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        // A JavaFX dialog cannot take ownership of a window that has no scene yet.
        if (owner != null && owner.getScene() != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("环境检测");
        alert.setHeaderText("部分功能当前不可用，请按提示处理");
        VBox content = new VBox(8);
        content.getChildren().addAll(warnings.stream().map(EnvironmentDialog::label).toList());
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(520);
        alert.showAndWait();
    }

    static List<String> warnings(AppConfig config, EnvironmentStatus status) {
        List<String> warnings = new ArrayList<>();
        if (!status.javaRuntimeAvailable()) {
            warnings.add("Java 运行环境版本过低，请使用 Java 21 或更高版本运行本程序。");
        }
        if (!status.ytDlpAvailable()) {
            warnings.add("未找到 yt-dlp：" + config.ytDlpPath()
                    + "。请从 yt-dlp 官方发布页下载 yt-dlp.exe，放入程序目录的 tools 文件夹，或在“设置”中指定路径。");
        }
        if (!status.ffmpegAvailable()) {
            warnings.add("未找到 FFmpeg：" + config.ffmpegPath()
                    + "。缺少 FFmpeg 时无法合并最佳画质的音视频流，请在“设置”中指定 ffmpeg.exe 路径。");
        }
        if (!status.downloadDirectoryWritable()) {
            warnings.add("下载目录不可写：" + config.downloadDirectory()
                    + "。请在“设置”中选择有写入权限的目录。");
        } else if (status.usableSpace() > 0 && status.usableSpace() < MINIMUM_FREE_SPACE_BYTES) {
            warnings.add("下载目录剩余空间不足 1 GB，当前可用：" + UiFormatters.freeSpace(status.usableSpace()));
        }
        return List.copyOf(warnings);
    }

    private static Label label(String text) {
        Label label = new Label("• " + text);
        label.setWrapText(true);
        return label;
    }
}
