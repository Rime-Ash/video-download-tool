package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.exception.StorageException;
import com.nzsk.videodownloader.exception.UrlValidationException;
import com.nzsk.videodownloader.model.AppConfig;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.DownloadState;
import com.nzsk.videodownloader.model.DownloadTask;
import com.nzsk.videodownloader.model.DownloadTaskId;
import com.nzsk.videodownloader.model.DouyinVideoFormat;
import com.nzsk.videodownloader.model.DouyinVideoInfo;
import com.nzsk.videodownloader.model.DouyinVideoRequest;
import com.nzsk.videodownloader.model.FormatInfo;
import com.nzsk.videodownloader.model.ImageInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.ImagePostRequest;
import com.nzsk.videodownloader.model.PostInspection;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.model.VideoInfo;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Download tab: link inspection, format selection, the task queue and the completion actions.
 */
final class DownloadPane {
    private static final int THUMBNAIL_WIDTH = 176;
    private static final int THUMBNAIL_HEIGHT = 99;

    private final AppContext context;
    private final BorderPane root = new BorderPane();

    private final TextField urlField = new TextField();
    private final Button inspectButton = new Button("解析视频");
    private final Label statusLabel = new Label("粘贴 bilibili 或抖音视频链接后点击“解析视频”。");
    private final Label titleLabel = new Label("尚未解析视频");
    private final Label uploaderLabel = new Label("作者：-");
    private final Label durationLabel = new Label("时长：-");
    private final ImageView thumbnailView = new ImageView();
    private final TableView<FormatInfo> formatTable = new TableView<>();
    private final TableView<DownloadTask> taskTable = new TableView<>();
    private final ObservableList<DownloadTask> taskItems = FXCollections.observableArrayList();

    private final Button downloadButton = new Button("加入下载队列");
    private final Button resumeButton = new Button("开始/继续");
    private final Button pauseButton = new Button("暂停");
    private final Button cancelButton = new Button("取消");
    private final Button retryButton = new Button("重试");
    private final Button clearButton = new Button("清空已完成");
    private final Button openFileButton = new Button("打开文件");
    private final Button openDirectoryButton = new Button("打开所在目录");
    private final Button copyPathButton = new Button("复制文件路径");

    private final Timeline refreshTimeline;
    private ValidatedUrl validatedUrl;
    private VideoInfo videoInfo;
    private ImagePostInfo imagePostInfo;
    private DouyinVideoInfo douyinVideoInfo;
    private List<DouyinVideoFormat> douyinFormats = List.of();

    DownloadPane(Stage owner, AppContext context) {
        Objects.requireNonNull(owner, "owner");
        this.context = Objects.requireNonNull(context, "context");
        root.setCenter(buildContent());
        refreshTimeline = new Timeline(new KeyFrame(Duration.millis(500), event -> refreshTasks()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
        updateActionStates();
    }

    Node node() {
        return root;
    }

    void stop() {
        refreshTimeline.stop();
    }

    void refreshConfiguration() {
        statusLabel.setText("下载目录：" + context.config().downloadDirectory());
    }

    private Node buildContent() {
        urlField.setPromptText("https://www.bilibili.com/video/... 或 https://v.douyin.com/...");
        HBox.setHgrow(urlField, Priority.ALWAYS);
        inspectButton.setDefaultButton(true);
        inspectButton.setOnAction(event -> inspectUrl());

        HBox inputRow = new HBox(10, urlField, inspectButton);
        VBox header = new VBox(8, inputRow, statusLabel);
        header.setPadding(new Insets(0, 0, 10, 0));

        SplitPane split = new SplitPane(buildVideoSection(), buildQueueSection());
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.42);

        BorderPane content = new BorderPane();
        content.setTop(header);
        content.setCenter(split);
        content.setPadding(new Insets(14));
        return content;
    }

    private Node buildVideoSection() {
        thumbnailView.setFitWidth(THUMBNAIL_WIDTH);
        thumbnailView.setFitHeight(THUMBNAIL_HEIGHT);
        thumbnailView.setPreserveRatio(true);

        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        VBox infoBox = new VBox(6, titleLabel, uploaderLabel, durationLabel);
        HBox infoRow = new HBox(12, thumbnailView, infoBox);
        infoRow.setAlignment(Pos.TOP_LEFT);

        configureFormatTable();
        downloadButton.setOnAction(event -> startDownload());
        downloadButton.setTooltip(new Tooltip("未选择格式时使用设置中的默认清晰度策略"));
        HBox actions = new HBox(10, downloadButton);

        VBox videoSection = new VBox(10, infoRow, new Label("可用清晰度与格式"), formatTable, actions);
        videoSection.setPadding(new Insets(10));
        videoSection.setMinHeight(220);
        return new TitledPane("视频信息", videoSection) {
            {
                setCollapsible(false);
            }
        };
    }

    private void configureFormatTable() {
        TableColumn<FormatInfo, String> idColumn = new TableColumn<>("格式");
        idColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().formatId()));
        TableColumn<FormatInfo, String> extensionColumn = new TableColumn<>("容器");
        extensionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().extension()));
        TableColumn<FormatInfo, String> resolutionColumn = new TableColumn<>("分辨率");
        resolutionColumn.setCellValueFactory(data -> new SimpleStringProperty(
                resolutionText(data.getValue())));
        TableColumn<FormatInfo, String> codecColumn = new TableColumn<>("编码");
        codecColumn.setCellValueFactory(data -> new SimpleStringProperty(
                UiFormatters.unknownIfBlank(data.getValue().videoCodec())
                        + " / " + UiFormatters.unknownIfBlank(data.getValue().audioCodec())));
        TableColumn<FormatInfo, String> sizeColumn = new TableColumn<>("大小");
        sizeColumn.setCellValueFactory(data -> new SimpleStringProperty(
                UiFormatters.fileSize(data.getValue().fileSize())));
        TableColumn<FormatInfo, String> noteColumn = new TableColumn<>("说明");
        noteColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().note()));

        formatTable.getColumns().setAll(List.of(
                idColumn, extensionColumn, resolutionColumn, codecColumn, sizeColumn, noteColumn));
        formatTable.setPlaceholder(new Label("解析成功后显示可用格式"));
        formatTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        formatTable.setPrefHeight(180);
        formatTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> updateActionStates());
    }

    private Node buildQueueSection() {
        configureTaskTable();
        taskTable.setItems(taskItems);
        taskTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> updateActionStates());

        resumeButton.setOnAction(event -> withSelectedTask(task -> {
            context.downloadQueue().resume(task.id());
            context.downloadQueue().start(task.id());
        }));
        pauseButton.setOnAction(event -> withSelectedTask(task ->
                context.downloadQueue().pause(task.id())));
        cancelButton.setOnAction(event -> withSelectedTask(task ->
                context.downloadQueue().cancel(task.id())));
        retryButton.setOnAction(event -> withSelectedTask(task ->
                context.downloadQueue().retry(task.id())));
        clearButton.setOnAction(event -> {
            context.downloadQueue().removeCompletedTasks();
            refreshTasks();
        });
        openFileButton.setOnAction(event -> withSelectedTask(task ->
                outputFileOf(task).ifPresentOrElse(this::openFile,
                        () -> statusLabel.setText("该任务还没有生成文件，或下载尚未完成。"))));
        openDirectoryButton.setOnAction(event -> withSelectedTask(this::openTaskDirectory));
        copyPathButton.setOnAction(event -> withSelectedTask(this::copyTaskPath));

        HBox taskActions = new HBox(8, resumeButton, pauseButton, cancelButton, retryButton, clearButton);
        HBox fileActions = new HBox(8, openFileButton, openDirectoryButton, copyPathButton);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, taskActions, spacer, fileActions);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox queueSection = new VBox(10, taskTable, actions);
        queueSection.setPadding(new Insets(10));
        VBox.setVgrow(taskTable, Priority.ALWAYS);
        return new TitledPane("下载队列", queueSection) {
            {
                setCollapsible(false);
            }
        };
    }

    private void configureTaskTable() {
        TableColumn<DownloadTask, String> fileColumn = new TableColumn<>("文件名");
        fileColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fileName()));
        fileColumn.setPrefWidth(220);

        TableColumn<DownloadTask, String> formatColumn = new TableColumn<>("清晰度");
        formatColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().summary()));
        formatColumn.setPrefWidth(150);

        TableColumn<DownloadTask, DownloadTask> progressColumn = new TableColumn<>("进度");
        progressColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        progressColumn.setCellFactory(column -> new ProgressCell());
        progressColumn.setPrefWidth(170);

        TableColumn<DownloadTask, String> speedColumn = new TableColumn<>("速度");
        speedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                UiFormatters.unknownIfBlank(data.getValue().progress().speed())));
        TableColumn<DownloadTask, String> etaColumn = new TableColumn<>("剩余时间");
        etaColumn.setCellValueFactory(data -> new SimpleStringProperty(
                UiFormatters.unknownIfBlank(data.getValue().progress().eta())));
        TableColumn<DownloadTask, String> stateColumn = new TableColumn<>("状态");
        stateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                UiFormatters.stateText(data.getValue().state())));
        TableColumn<DownloadTask, String> errorColumn = new TableColumn<>("错误信息");
        errorColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().errorMessage() == null ? "" : data.getValue().errorMessage()));
        errorColumn.setPrefWidth(200);

        taskTable.getColumns().setAll(List.of(fileColumn, formatColumn, progressColumn,
                speedColumn, etaColumn, stateColumn, errorColumn));
        taskTable.setPlaceholder(new Label("暂无下载任务"));
        taskTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void inspectUrl() {
        String rawUrl = urlField.getText();
        inspectButton.setDisable(true);
        statusLabel.setText("正在校验链接并解析作品信息...");
        Task<InspectedPost> task = new Task<>() {
            @Override
            protected InspectedPost call() throws Exception {
                ValidatedUrl url = context.validateUrl(rawUrl);
                return new InspectedPost(url, context.inspectPost(url));
            }
        };
        task.setOnSucceeded(event -> {
            InspectedPost inspected = task.getValue();
            validatedUrl = inspected.url();
            videoInfo = inspected.inspection().video();
            imagePostInfo = inspected.inspection().imagePost();
            douyinVideoInfo = inspected.inspection().douyinVideo();
            if (douyinVideoInfo == null) {
                douyinFormats = List.of();
            }
            if (videoInfo != null) {
                showVideoInfo(videoInfo);
            } else if (douyinVideoInfo != null) {
                showDouyinVideoInfo(douyinVideoInfo);
            } else {
                showImagePostInfo(imagePostInfo);
            }
            String normalizedUrl = inspected.url().uri().toString();
            if (rawUrl != null && !normalizedUrl.equalsIgnoreCase(rawUrl.trim())) {
                statusLabel.setText("已把链接还原为：" + normalizedUrl
                        + "。如果解析失败，请在作品页用“分享 → 复制链接”重新获取链接。");
            } else if (douyinVideoInfo != null) {
                statusLabel.setText("解析完成：yt-dlp 无法解析该抖音链接，已改用浏览器兜底解析（"
                        + douyinVideoInfo.formats().size()
                        + " 个可下载源）。请选择清晰度，或直接加入队列。");
            } else if (imagePostInfo != null) {
                long liveCount = imagePostInfo.images().stream().filter(ImageInfo::hasLivePhoto).count();
                statusLabel.setText("解析完成：这是一条图文作品，共 " + imagePostInfo.imageCount()
                        + " 张图片" + (liveCount > 0
                        ? "（其中 " + liveCount + " 张是动图，会同时保存配套短视频）" : "")
                        + "，点击“加入下载队列”即可全部保存。");
            } else {
                statusLabel.setText("解析完成：请选择清晰度，或直接加入队列使用默认策略。");
            }
            inspectButton.setDisable(false);
            updateActionStates();
        });
        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            statusLabel.setText("解析失败：" + describe(exception));
            inspectButton.setDisable(false);
        });
        context.runInBackground(task);
    }

    private void showVideoInfo(VideoInfo info) {
        titleLabel.setText("标题：" + UiFormatters.unknownIfBlank(info.title()));
        uploaderLabel.setText("作者：" + UiFormatters.unknownIfBlank(info.uploader()));
        durationLabel.setText("时长：" + UiFormatters.duration(info.durationSeconds()));
        formatTable.getItems().setAll(info.formats().stream()
                .sorted(Comparator.comparing(
                        (FormatInfo format) -> format.height() == null ? -1 : format.height())
                        .reversed())
                .toList());
        if (!formatTable.getItems().isEmpty()) {
            formatTable.getSelectionModel().selectFirst();
        }
        loadThumbnail(info.thumbnailUrl());
    }

    /** Image posts reuse the format table so the layout stays identical to the video flow. */
    private void showImagePostInfo(ImagePostInfo info) {
        if (info == null) {
            return;
        }
        titleLabel.setText("标题：" + UiFormatters.unknownIfBlank(info.title()));
        uploaderLabel.setText("作者：" + UiFormatters.unknownIfBlank(info.author()));
        long livePhotos = info.images().stream().filter(ImageInfo::hasLivePhoto).count();
        durationLabel.setText("类型：图文作品，共 " + info.imageCount() + " 张图片"
                + (livePhotos > 0 ? "（含 " + livePhotos + " 张动图）" : ""));
        List<FormatInfo> rows = new java.util.ArrayList<>();
        List<ImageInfo> images = info.images();
        for (int index = 0; index < images.size(); index++) {
            ImageInfo image = images.get(index);
            String note = image.resolution()
                    + (image.hasLivePhoto() ? " · 动图 " + image.livePhoto().durationText() : "");
            rows.add(new FormatInfo(
                    "图片 " + (index + 1),
                    image.extension(),
                    "图片",
                    "—",
                    image.width(),
                    image.height(),
                    null,
                    note));
        }
        formatTable.getItems().setAll(rows);
        formatTable.getSelectionModel().clearSelection();
        loadThumbnail(images.isEmpty() ? null : images.get(0).url());
    }

    /**
     * Douyin fallback videos reuse the format table, so the layout stays identical to the yt-dlp flow. The
     * page only exposes playable addresses, so the rows carry no resolution or codec details.
     */
    private void showDouyinVideoInfo(DouyinVideoInfo info) {
        titleLabel.setText("标题：" + UiFormatters.unknownIfBlank(info.title()));
        uploaderLabel.setText("作者：" + UiFormatters.unknownIfBlank(info.author()));
        durationLabel.setText("类型：抖音视频（浏览器兜底解析）");
        List<FormatInfo> rows = new java.util.ArrayList<>();
        for (DouyinVideoFormat format : info.formats()) {
            rows.add(new FormatInfo(format.id(), format.extension(), "视频", "已包含",
                    null, null, null, format.note()));
        }
        douyinFormats = info.formats();
        formatTable.getItems().setAll(rows);
        if (!rows.isEmpty()) {
            formatTable.getSelectionModel().selectFirst();
        }
        loadThumbnail(info.thumbnailUrl());
    }

    private void loadThumbnail(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            thumbnailView.setImage(null);
            return;
        }
        try {
            thumbnailView.setImage(new Image(thumbnailUrl, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                    true, true, true));
        } catch (IllegalArgumentException exception) {
            thumbnailView.setImage(null);
        }
    }

    private void startDownload() {
        if (validatedUrl == null
                || (videoInfo == null && imagePostInfo == null && douyinVideoInfo == null)) {
            statusLabel.setText("请先解析作品链接。");
            return;
        }
        AppConfig config = context.config();
        try {
            if (douyinVideoInfo != null) {
                startDouyinFallbackDownload(config);
                return;
            }
            if (imagePostInfo != null) {
                Path folderBase = context.pathSecurity()
                        .resolveUniqueBaseName(config.downloadDirectory(), imagePostInfo.title());
                ImagePostRequest request = new ImagePostRequest(
                        validatedUrl,
                        imagePostInfo.postId(),
                        folderBase.getFileName().toString(),
                        imagePostInfo.images(),
                        config.downloadDirectory(),
                        config.cookieFile());
                DownloadTask task = context.downloadQueue().addImagePostTask(request);
                context.downloadQueue().start(task.id());
                refreshTasks();
                statusLabel.setText("已加入队列：" + task.fileName() + "（图文作品，共 "
                        + imagePostInfo.imageCount() + " 张图片）");
                if (config.cookieFile() == null) {
                    statusLabel.setText(statusLabel.getText()
                            + "　提示：未配置 Cookie 文件时，抖音图集接口通常会拒绝访问。");
                }
                return;
            }
            FormatInfo selectedFormat = formatTable.getSelectionModel().getSelectedItem();
            String selector = selectedFormat == null
                    ? config.defaultFormatSelector()
                    : selectorFor(selectedFormat);
            Path baseName = context.pathSecurity()
                    .resolveUniqueBaseName(config.downloadDirectory(), videoInfo.title());
            DownloadRequest request = new DownloadRequest(
                    validatedUrl,
                    selector,
                    config.downloadDirectory(),
                    baseName.getFileName().toString(),
                    true,
                    context.downloadOptions());
            DownloadTask task = context.downloadQueue().addTask(request);
            context.downloadQueue().start(task.id());
            refreshTasks();
            statusLabel.setText("已加入下载队列：" + task.fileName());
        } catch (StorageException | IllegalArgumentException exception) {
            statusLabel.setText("无法开始下载：" + exception.getMessage());
        }
    }

    private void startDouyinFallbackDownload(AppConfig config) throws StorageException {
        int index = formatTable.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= douyinFormats.size()) {
            statusLabel.setText("请先选择要下载的视频源。");
            return;
        }
        String title = douyinVideoInfo.title() == null || douyinVideoInfo.title().isBlank()
                ? "douyin-" + douyinVideoInfo.postId()
                : douyinVideoInfo.title();
        Path baseName = context.pathSecurity()
                .resolveUniqueBaseName(config.downloadDirectory(), title);
        DouyinVideoRequest request = new DouyinVideoRequest(
                validatedUrl,
                douyinVideoInfo.postId(),
                baseName.getFileName().toString(),
                douyinFormats.get(index),
                config.downloadDirectory(),
                config.cookieFile());
        DownloadTask task = context.downloadQueue().addDouyinVideoTask(request);
        context.downloadQueue().start(task.id());
        refreshTasks();
        statusLabel.setText("已加入下载队列：" + task.fileName() + "（浏览器兜底解析）");
    }

    /**
     * The resolution column shows "仅音频" only for audio-only streams; a fallback video row has no
     * resolution information at all.
     */
    private static String resolutionText(FormatInfo format) {
        if (format.height() != null) {
            return format.height() + "p";
        }
        String videoCodec = format.videoCodec();
        return videoCodec == null || videoCodec.isBlank() || videoCodec.equalsIgnoreCase("none")
                ? "仅音频"
                : "未知";
    }

    /** Video-only formats are combined with the best audio stream so FFmpeg can merge them. */
    private static String selectorFor(FormatInfo format) {
        String videoCodec = format.videoCodec();
        if (videoCodec == null || videoCodec.isBlank() || videoCodec.equalsIgnoreCase("none")) {
            return "bestaudio/best";
        }
        String audioCodec = format.audioCodec();
        if (audioCodec == null || audioCodec.isBlank() || audioCodec.equalsIgnoreCase("none")) {
            return format.formatId() + "+bestaudio/" + format.formatId();
        }
        return format.formatId();
    }

    private void refreshTasks() {
        DownloadTaskId selectedId = selectedTaskId();
        List<DownloadTask> snapshot = context.downloadQueue().snapshot();
        for (int index = 0; index < snapshot.size(); index++) {
            DownloadTask task = snapshot.get(index);
            if (index < taskItems.size() && taskItems.get(index).id().equals(task.id())) {
                if (!taskItems.get(index).equals(task)) {
                    taskItems.set(index, task);
                }
            } else {
                int existingIndex = indexOfTask(task.id());
                if (existingIndex >= 0) {
                    taskItems.set(existingIndex, task);
                } else {
                    taskItems.add(index, task);
                }
            }
        }
        while (taskItems.size() > snapshot.size()) {
            taskItems.remove(taskItems.size() - 1);
        }
        if (selectedId != null) {
            int index = indexOfTask(selectedId);
            if (index >= 0 && !selectedId.equals(selectedTaskId())) {
                taskTable.getSelectionModel().select(index);
            }
        }
        updateActionStates();
    }

    private int indexOfTask(DownloadTaskId taskId) {
        for (int index = 0; index < taskItems.size(); index++) {
            if (taskItems.get(index).id().equals(taskId)) {
                return index;
            }
        }
        return -1;
    }

    private DownloadTaskId selectedTaskId() {
        DownloadTask task = taskTable.getSelectionModel().getSelectedItem();
        return task == null ? null : task.id();
    }

    private void withSelectedTask(java.util.function.Consumer<DownloadTask> action) {
        DownloadTask task = taskTable.getSelectionModel().getSelectedItem();
        if (task == null) {
            statusLabel.setText("请先在下载队列中选择一个任务。");
            return;
        }
        action.accept(task);
        refreshTasks();
    }

    private void updateActionStates() {
        DownloadTask selected = taskTable.getSelectionModel().getSelectedItem();
        DownloadState state = selected == null ? null : selected.state();
        boolean hasSelection = state != null;
        resumeButton.setDisable(!hasSelection
                || !(state == DownloadState.PAUSED || state == DownloadState.QUEUED
                || state == DownloadState.FAILED || state == DownloadState.CANCELLED));
        pauseButton.setDisable(!hasSelection
                || (state != DownloadState.DOWNLOADING && state != DownloadState.QUEUED
                && state != DownloadState.MERGING && state != DownloadState.PARSING));
        cancelButton.setDisable(!hasSelection
                || state == DownloadState.COMPLETED || state == DownloadState.CANCELLED);
        retryButton.setDisable(!hasSelection
                || (state != DownloadState.FAILED && state != DownloadState.CANCELLED));
        boolean hasOutput = hasSelection && selected.outputFile() != null;
        openFileButton.setDisable(!hasOutput);
        copyPathButton.setDisable(!hasOutput);
        downloadButton.setDisable(videoInfo == null && imagePostInfo == null && douyinVideoInfo == null);
    }

    private void openFile(Path file) {
        try {
            context.localFileService().open(file);
        } catch (StorageException exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    private void openTaskDirectory(DownloadTask task) {
        Path directory = task.outputFile() != null && task.outputFile().getParent() != null
                ? task.outputFile().getParent()
                : context.config().downloadDirectory();
        try {
            context.localFileService().openDirectory(directory);
        } catch (StorageException exception) {
            statusLabel.setText(exception.getMessage());
        }
    }

    private void copyTaskPath(DownloadTask task) {
        outputFileOf(task).ifPresentOrElse(path -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(path.toString());
            Clipboard.getSystemClipboard().setContent(content);
            statusLabel.setText("已复制文件路径。");
        }, () -> statusLabel.setText("该任务还没有生成文件路径。"));
    }

    private static java.util.Optional<Path> outputFileOf(DownloadTask task) {
        return java.util.Optional.ofNullable(task.outputFile());
    }

    private static String describe(Throwable exception) {
        if (exception == null) {
            return "未知错误。";
        }
        if (exception instanceof UrlValidationException || exception instanceof StorageException) {
            return exception.getMessage();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "请检查 yt-dlp 路径与网络连接。" : message;
    }

    private record InspectedPost(ValidatedUrl url, PostInspection inspection) {
    }

    private static final class ProgressCell extends TableCell<DownloadTask, DownloadTask> {
        private final ProgressBar progressBar = new ProgressBar(0);
        private final Label percentLabel = new Label();
        private final HBox box = new HBox(6, progressBar, percentLabel);

        private ProgressCell() {
            progressBar.setPrefWidth(110);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(DownloadTask task, boolean empty) {
            super.updateItem(task, empty);
            if (empty || task == null) {
                setGraphic(null);
                return;
            }
            double percentage = task.progress().percentage();
            progressBar.setProgress(percentage / 100.0);
            percentLabel.setText(task.progress().downloadedSize() == null
                    || task.progress().downloadedSize().isBlank()
                    ? String.format("%.0f%%", percentage)
                    : String.format("%.0f%% · %s", percentage, task.progress().downloadedSize()));
            setGraphic(box);
        }
    }
}
