package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.exception.ConfigurationException;
import com.nzsk.videodownloader.exception.StorageException;
import com.nzsk.videodownloader.model.AppConfig;
import com.nzsk.videodownloader.model.EnvironmentStatus;
import com.nzsk.videodownloader.util.AppPaths;
import com.nzsk.videodownloader.util.BrowserCookieSource;
import com.nzsk.videodownloader.util.LogManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Settings tab. Every value is persisted through {@code AppConfig} and never hard-coded.
 */
final class SettingsPane {
    private static final List<String> FORMAT_STRATEGIES = List.of(
            AppPaths.DEFAULT_FORMAT_SELECTOR,
            "best",
            "bestvideo/best",
            "worst");
    private static final String NO_BROWSER = "(不使用)";
    private static final List<String> COOKIE_BROWSERS =
            List.of(NO_BROWSER, "chrome", "edge", "firefox", "brave", "opera", "vivaldi", "chromium");

    private final Stage owner;
    private final AppContext context;
    private final Runnable onSaved;
    private final ScrollPane root = new ScrollPane();

    private final TextField downloadDirectoryField = new TextField();
    private final Spinner<Integer> concurrencySpinner =
            new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 4, 1));
    private final TextField rateLimitField = new TextField();
    private final TextField proxyField = new TextField();
    private final TextField cookieFileField = new TextField();
    private final ComboBox<String> cookieBrowserBox = new ComboBox<>();
    private final ComboBox<String> formatStrategyBox = new ComboBox<>();
    private final TextField ytDlpPathField = new TextField();
    private final TextField ffmpegPathField = new TextField();
    private final Label messageLabel = new Label();
    private final Label browserSupportLabel = new Label();
    private final VBox environmentBox = new VBox(6);

    SettingsPane(Stage owner, AppContext context, Runnable onSaved) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.context = Objects.requireNonNull(context, "context");
        this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
        root.setFitToWidth(true);
        root.setContent(buildContent());
        populate();
        refreshEnvironment();
    }

    Node node() {
        return root;
    }

    private Node buildContent() {
        formatStrategyBox.setEditable(true);
        formatStrategyBox.getItems().setAll(FORMAT_STRATEGIES);
        concurrencySpinner.setEditable(true);
        cookieBrowserBox.getItems().setAll(COOKIE_BROWSERS);
        cookieBrowserBox.setValue(NO_BROWSER);
        configureFields();

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setMinWidth(360);
        fieldColumn.setFillWidth(true);
        fieldColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints extraColumn = new ColumnConstraints();
        extraColumn.setMinWidth(96);
        form.getColumnConstraints().addAll(labelColumn, fieldColumn, extraColumn);

        addRow(form, 0, "下载目录", downloadDirectoryField,
                button("选择目录", this::chooseDownloadDirectory));
        addRow(form, 1, "最大并发数", concurrencySpinner, null);
        addRow(form, 2, "下载限速", rateLimitField, null);
        addRow(form, 3, "HTTP/HTTPS 代理", proxyField, null);
        addRow(form, 4, "Cookie 文件", cookieFileField, button("选择文件", this::chooseCookieFile));
        addRow(form, 5, "从浏览器读取 Cookie", cookieBrowserBox, null);
        addRow(form, 6, "默认清晰度策略", formatStrategyBox, null);
        addRow(form, 7, "yt-dlp 路径", ytDlpPathField, button("选择文件", this::chooseYtDlp));
        addRow(form, 8, "FFmpeg 路径", ffmpegPathField, button("选择文件", this::chooseFFmpeg));

        Button saveButton = new Button("保存设置");
        saveButton.setDefaultButton(true);
        saveButton.setOnAction(event -> save());
        messageLabel.setWrapText(true);
        HBox saveRow = new HBox(10, saveButton, messageLabel);
        saveRow.setAlignment(Pos.CENTER_LEFT);

        VBox settingsCard = new VBox(12, form, browserSupportLabel, saveRow);
        settingsCard.setPadding(new Insets(14));
        TitledPane settingsPane = new TitledPane("下载与工具配置", settingsCard);
        settingsPane.setCollapsible(false);

        Button recheckButton = new Button("重新检测");
        recheckButton.setOnAction(event -> refreshEnvironment());
        HBox environmentHeader = new HBox(10, new Label("环境检测"), recheckButton);
        environmentHeader.setAlignment(Pos.CENTER_LEFT);
        VBox environmentCard = new VBox(10, environmentHeader, environmentBox, buildDirectoryActions());
        environmentCard.setPadding(new Insets(14));
        TitledPane environmentPane = new TitledPane("运行环境", environmentCard);
        environmentPane.setCollapsible(false);

        VBox complianceCard = new VBox(ComplianceNotice.content());
        complianceCard.setPadding(new Insets(14));
        TitledPane compliancePane = new TitledPane("合规声明", complianceCard);
        compliancePane.setCollapsible(false);

        Label hint = new Label("提示：最大并发数保存后立即生效。若更换 yt-dlp 路径，正在运行的任务需要重新开始。"
                + " Cookie 文件只在调用 yt-dlp 时引用，不会复制到临时目录、写入日志或上传。");
        hint.setWrapText(true);

        VBox content = new VBox(12, settingsPane, environmentPane, compliancePane, hint);
        content.setPadding(new Insets(14));
        return content;
    }

    /**
     * JavaFX text fields default to a width of about twelve characters and ignore their prompt text,
     * which truncated long paths. Every text field therefore grows with the available width, and the
     * longer explanations are moved to tooltips so they cannot squeeze the input column.
     */
    private void configureFields() {
        configureTextField(downloadDirectoryField, "下载完成的视频保存到这个目录。");
        configureTextField(rateLimitField, "留空表示不限速，例如 500K、1M、1.5M。");
        configureTextField(proxyField, "留空表示不使用代理，例如 http://127.0.0.1:7890。");
        configureTextField(cookieFileField,
                "留空表示不使用 Cookie 文件。文件只被就地引用，不会复制到临时目录或写入日志。");
        configureTextField(ytDlpPathField, "yt-dlp.exe 的完整路径。");
        configureTextField(ffmpegPathField, "ffmpeg.exe 的完整路径，用于合并最佳画质的音视频流。");

        rateLimitField.setPromptText("留空表示不限速，例如 2M");
        proxyField.setPromptText("留空表示不使用代理，例如 http://127.0.0.1:7890");
        cookieFileField.setPromptText("留空表示不使用 Cookie 文件");

        concurrencySpinner.setPrefWidth(120);
        concurrencySpinner.setMaxWidth(160);
        cookieBrowserBox.setPrefWidth(220);
        cookieBrowserBox.setMaxWidth(260);
        cookieBrowserBox.setTooltip(new Tooltip("由 yt-dlp 直接读取本机浏览器数据，与上面的 Cookie 文件二选一。"
                + "读取前需完全退出浏览器（含后台进程）；Edge 与新版 Chrome 使用应用绑定加密，"
                + "这种情况请改用导出的 Cookie 文件，或使用 Firefox。"
                + "两种来源只能用一个：选择浏览器 Cookie 会清空上面的 Cookie 文件；"
                + "设置了 Cookie 文件时以文件为准。"));
        formatStrategyBox.setPrefWidth(360);
        formatStrategyBox.setMaxWidth(Double.MAX_VALUE);
        formatStrategyBox.setTooltip(new Tooltip("未手动选择清晰度时使用的格式策略，默认 bestvideo+bestaudio/best。"));

        // 两种 Cookie 来源互斥，避免出现"设置了文件却仍去读浏览器"的混淆情况
        cookieBrowserBox.valueProperty().addListener((observable, previous, current) -> {
            if (current != null && !NO_BROWSER.equals(current) && !cookieFileField.getText().isBlank()) {
                cookieFileField.clear();
                messageLabel.setText("已切换为浏览器 Cookie，Cookie 文件已清空。");
            }
            updateBrowserSupport();
        });
        browserSupportLabel.setWrapText(true);
        browserSupportLabel.setMaxWidth(Double.MAX_VALUE);
    }

    private void configureTextField(TextField field, String tooltip) {
        field.setPrefWidth(420);
        field.setMinWidth(240);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setTooltip(new Tooltip(tooltip));
    }

    private Node buildDirectoryActions() {
        HBox row = new HBox(10,
                button("打开配置目录", () -> openDirectory(AppPaths.configDirectory())),
                button("打开日志目录", () -> openDirectory(LogManager.logDirectory())));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void addRow(GridPane form, int row, String label, Node field, Node extra) {
        form.add(new Label(label), 0, row);
        form.add(field, 1, row);
        if (extra != null) {
            form.add(extra, 2, row);
        }
    }

    private void populate() {
        AppConfig config = context.config();
        downloadDirectoryField.setText(config.downloadDirectory().toString());
        concurrencySpinner.getValueFactory().setValue(config.maxConcurrentDownloads());
        rateLimitField.setText(config.rateLimit() == null ? "" : config.rateLimit());
        proxyField.setText(config.proxy() == null ? "" : config.proxy().toString());
        cookieFileField.setText(config.cookieFile() == null ? "" : config.cookieFile().toString());
        boolean bothCookieSourcesConfigured =
                config.cookieFile() != null && config.cookieBrowser() != null;
        cookieBrowserBox.setValue(bothCookieSourcesConfigured || config.cookieBrowser() == null
                ? NO_BROWSER
                : config.cookieBrowser());
        if (bothCookieSourcesConfigured) {
            messageLabel.setText("检测到同时配置了 Cookie 文件与浏览器 Cookie：已按 Cookie 文件生效，"
                    + "浏览器来源已设为“不使用”。");
        }
        formatStrategyBox.setValue(config.defaultFormatSelector());
        ytDlpPathField.setText(config.ytDlpPath().toString());
        ffmpegPathField.setText(config.ffmpegPath().toString());
        updateBrowserSupport();
    }

    /**
     * Shows up front whether the selected browser profile can be read at all, instead of letting the user
     * discover it through a failing download. Chromium 127+ uses app-bound encryption, which yt-dlp cannot
     * decrypt even when the browser is closed.
     */
    private void updateBrowserSupport() {
        String selected = cookieBrowserBox.getValue();
        BrowserCookieSource.Support support =
                BrowserCookieSource.check(selected == null || NO_BROWSER.equals(selected) ? null : selected);
        browserSupportLabel.setText(support.readable() ? support.message() : "⚠ " + support.message());
        browserSupportLabel.setStyle(support.readable()
                ? "-fx-text-fill: #1b7f3b;"
                : "-fx-text-fill: #b3261e;");
    }

    private void save() {
        try {
            context.applyConfig(buildConfig());
            messageLabel.setText("设置已保存。");
            refreshEnvironment();
            onSaved.run();
        } catch (ConfigurationException | IllegalArgumentException exception) {
            messageLabel.setText("保存失败：" + exception.getMessage());
        }
    }

    private AppConfig buildConfig() {
        String cookieFile = blankToNull(cookieFileField.getText());
        String selectedBrowser = cookieBrowserBox.getValue();
        String cookieBrowser = cookieFile != null || selectedBrowser == null || NO_BROWSER.equals(selectedBrowser)
                ? null
                : selectedBrowser;
        return new AppConfig(
                Path.of(required(downloadDirectoryField.getText(), "下载目录")),
                concurrencySpinner.getValue(),
                blankToNull(rateLimitField.getText()),
                AppPaths.parseProxy(proxyField.getText()),
                cookieFile == null ? null : Path.of(cookieFile),
                cookieBrowser,
                required(formatStrategyBox.getValue(), "默认清晰度策略"),
                Path.of(required(ytDlpPathField.getText(), "yt-dlp 路径")),
                Path.of(required(ffmpegPathField.getText(), "FFmpeg 路径")));
    }

    private void refreshEnvironment() {
        AppConfig config = context.config();
        EnvironmentStatus status = context.environmentStatus();
        environmentBox.getChildren().setAll(List.of(
                statusLine(status.javaRuntimeAvailable(), "Java 运行环境",
                        "版本满足要求", "版本过低，请使用 Java 21 或更高版本运行"),
                statusLine(status.ytDlpAvailable(), "yt-dlp", "已找到可执行文件",
                        "未找到：" + config.ytDlpPath() + "，请指定路径或放入程序目录的 tools 文件夹"),
                statusLine(status.ffmpegAvailable(), "FFmpeg", "已找到可执行文件",
                        "未找到：" + config.ffmpegPath() + "，缺少时无法合并最佳画质的音视频流"),
                statusLine(status.downloadDirectoryWritable(), "下载目录", "可写入",
                        "不可写入，请重新选择有权限的目录"),
                new Label("磁盘剩余空间：" + UiFormatters.freeSpace(status.usableSpace()))));
    }

    private Label statusLine(boolean available, String name, String okText, String problemText) {
        Label label = new Label((available ? "✓ " : "✗ ") + name + "：" + (available ? okText : problemText));
        label.setWrapText(true);
        label.setStyle(available ? "-fx-text-fill: #1b7f3b;" : "-fx-text-fill: #b3261e;");
        return label;
    }

    private void chooseDownloadDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择下载目录");
        File initial = existingDirectory(downloadDirectoryField.getText());
        if (initial != null) {
            chooser.setInitialDirectory(initial);
        }
        File directory = chooser.showDialog(owner);
        if (directory != null) {
            downloadDirectoryField.setText(directory.getAbsolutePath());
        }
    }

    private void chooseCookieFile() {
        File file = chooseFile("选择 Cookie 文件（Netscape 格式）");
        if (file != null) {
            cookieFileField.setText(file.getAbsolutePath());
            if (!NO_BROWSER.equals(cookieBrowserBox.getValue())) {
                cookieBrowserBox.setValue(NO_BROWSER);
            }
            messageLabel.setText("已选择 Cookie 文件，将从该文件读取 Cookie。");
        }
    }

    private void chooseYtDlp() {
        File file = chooseFile("选择 yt-dlp.exe");
        if (file != null) {
            ytDlpPathField.setText(file.getAbsolutePath());
        }
    }

    private void chooseFFmpeg() {
        File file = chooseFile("选择 ffmpeg.exe");
        if (file != null) {
            ffmpegPathField.setText(file.getAbsolutePath());
        }
    }

    private File chooseFile(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("可执行文件", "*.exe"),
                new FileChooser.ExtensionFilter("所有文件", "*.*"));
        return chooser.showOpenDialog(owner);
    }

    private File existingDirectory(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            return null;
        }
        File file = new File(pathText.trim());
        return file.isDirectory() ? file : null;
    }

    private void openDirectory(Path directory) {
        try {
            context.localFileService().openDirectory(directory);
        } catch (StorageException exception) {
            messageLabel.setText(exception.getMessage());
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空。");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
