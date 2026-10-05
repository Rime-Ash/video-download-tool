package com.nzsk.videodownloader.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.engine.DefaultProcessExecutor;
import com.nzsk.videodownloader.engine.DefaultImagePostClient;
import com.nzsk.videodownloader.engine.DefaultDouyinVideoClient;
import com.nzsk.videodownloader.engine.DefaultYtDlpClient;
import com.nzsk.videodownloader.engine.DouyinPostIds;
import com.nzsk.videodownloader.engine.ImagePostClient;
import com.nzsk.videodownloader.engine.YtDlpClient;
import com.nzsk.videodownloader.exception.ConfigurationException;
import com.nzsk.videodownloader.exception.DouyinVideoException;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.exception.ProcessExecutionException;
import com.nzsk.videodownloader.exception.UrlValidationException;
import com.nzsk.videodownloader.model.AppConfig;
import com.nzsk.videodownloader.model.DownloadOptions;
import com.nzsk.videodownloader.model.EnvironmentStatus;
import com.nzsk.videodownloader.model.PostInspection;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.model.VideoInfo;
import com.nzsk.videodownloader.service.ConfigManager;
import com.nzsk.videodownloader.service.DownloadQueue;
import com.nzsk.videodownloader.service.EnvironmentCheckService;
import com.nzsk.videodownloader.service.HttpRedirectResolver;
import com.nzsk.videodownloader.service.JsonConfigManager;
import com.nzsk.videodownloader.service.LocalFileService;
import com.nzsk.videodownloader.service.RetryService;
import com.nzsk.videodownloader.util.AppPaths;
import com.nzsk.videodownloader.util.DefaultLocalFileService;
import com.nzsk.videodownloader.util.DefaultPathSecurity;
import com.nzsk.videodownloader.util.DefaultUrlValidator;
import com.nzsk.videodownloader.util.LogManager;
import com.nzsk.videodownloader.util.PathSecurity;
import com.nzsk.videodownloader.util.UrlValidator;
import javafx.concurrent.Task;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Composition root for the desktop application. It wires backend services together so that the
 * JavaFX views only talk to interfaces and never start processes or touch files directly.
 */
final class AppContext implements AutoCloseable {
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    private final Logger logger = LogManager.getLogger(AppContext.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultProcessExecutor processExecutor = new DefaultProcessExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final UrlValidator urlValidator =
            new DefaultUrlValidator(new HttpRedirectResolver(httpClient, MAX_REDIRECTS));
    private final ConfigManager configManager =
            new JsonConfigManager(AppPaths.configFile(), AppPaths.defaultConfig());
    private final EnvironmentCheckService environmentCheckService = new EnvironmentCheckService();
    private final PathSecurity pathSecurity = new DefaultPathSecurity();
    private final LocalFileService localFileService = new DefaultLocalFileService();
    private final ImagePostClient imagePostClient = new DefaultImagePostClient(objectMapper);
    private final DefaultDouyinVideoClient douyinVideoClient =
            new DefaultDouyinVideoClient(processExecutor);
    private final ExecutorService backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "video-downloader-background");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<AppConfig> config = new AtomicReference<>();
    private final DownloadQueue downloadQueue;

    AppContext() {
        LogManager.prepareLogDirectory();
        AppConfig loadedConfig = loadConfig();
        config.set(loadedConfig);
        downloadQueue = new DownloadQueue(
                createYtDlpClient(loadedConfig),
                imagePostClient,
                loadedConfig.maxConcurrentDownloads(),
                new RetryService(MAX_DOWNLOAD_ATTEMPTS, AppContext::sleep));
        downloadQueue.setDouyinVideoClient(douyinVideoClient);
    }

    AppConfig config() {
        return config.get();
    }

    DownloadQueue downloadQueue() {
        return downloadQueue;
    }

    PathSecurity pathSecurity() {
        return pathSecurity;
    }

    LocalFileService localFileService() {
        return localFileService;
    }

    EnvironmentStatus environmentStatus() {
        return environmentCheckService.check(config.get());
    }

    DownloadOptions downloadOptions() {
        AppConfig current = config.get();
        return new DownloadOptions(
                current.rateLimit(), current.proxy(), current.cookieFile(), current.ffmpegPath(),
                current.cookieBrowser());
    }

    ValidatedUrl validateUrl(String rawUrl) throws UrlValidationException {
        return urlValidator.validate(rawUrl);
    }

    VideoInfo inspectVideo(ValidatedUrl url) throws ProcessExecutionException {
        return createYtDlpClient(config.get()).inspect(url);
    }

    /**
     * Inspects a link and tells the caller whether it is a video or an image post (图文/图集).
     *
     * <p>A {@code /note/} link is always treated as an image post. A video link that cannot be parsed is
     * checked once more through the image post API, because share links of image posts occasionally carry a
     * video style URL. A Douyin link that also fails there is rendered through the browser fallback, which
     * works around Douyin refusing unsigned API requests.</p>
     */
    PostInspection inspectPost(ValidatedUrl url) throws ProcessExecutionException, ImagePostException {
        if (DouyinPostIds.isImagePostPath(url.uri())) {
            return PostInspection.ofImagePost(imagePostClient.inspect(url, config.get().cookieFile()));
        }
        try {
            return PostInspection.ofVideo(inspectVideo(url));
        } catch (ProcessExecutionException failure) {
            if (isDouyinHost(url)) {
                try {
                    return PostInspection.ofImagePost(imagePostClient.inspect(url, config.get().cookieFile()));
                } catch (ImagePostException ignored) {
                    // A /video/ link is usually not an image post; continue with the browser fallback.
                }
                try {
                    return PostInspection.ofDouyinVideo(
                            douyinVideoClient.inspect(url, config.get().cookieFile()));
                } catch (DouyinVideoException fallbackFailure) {
                    logger.debug("抖音浏览器兜底解析失败：{}", fallbackFailure.getMessage());
                }
            }
            // Keep the original video failure: it carries the platform's own reason for the refusal.
            throw failure;
        }
    }

    private static boolean isDouyinHost(ValidatedUrl url) {
        String host = url.host().toLowerCase(Locale.ROOT);
        return host.endsWith("douyin.com");
    }

    void applyConfig(AppConfig updated) throws ConfigurationException {
        Objects.requireNonNull(updated, "updated");
        configManager.save(updated);
        AppConfig previous = config.getAndSet(updated);
        downloadQueue.setMaxConcurrentDownloads(updated.maxConcurrentDownloads());
        if (!previous.ytDlpPath().equals(updated.ytDlpPath())) {
            downloadQueue.setYtDlpClient(createYtDlpClient(updated));
        }
    }

    /** Runs a JavaFX task on a daemon worker so the application thread stays responsive. */
    void runInBackground(Task<?> task) {
        backgroundExecutor.execute(task);
    }

    @Override
    public void close() {
        downloadQueue.close();
        backgroundExecutor.shutdownNow();
        processExecutor.close();
        if (imagePostClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                logger.debug("图集客户端关闭时出现异常。");
            }
        }
        try {
            douyinVideoClient.close();
        } catch (Exception exception) {
            logger.debug("抖音兜底客户端关闭时出现异常。");
        }
    }

    private AppConfig loadConfig() {
        AppConfig loaded;
        try {
            loaded = configManager.load();
        } catch (ConfigurationException exception) {
            logger.warn("配置文件无法读取，已使用默认配置。");
            loaded = AppPaths.defaultConfig();
        }
        return withRepairedToolPaths(loaded);
    }

    /**
     * The configuration survives reinstalls, so a tool path can point at an installation directory that no
     * longer exists. A configured path is kept while the file is there; otherwise the tool shipped next to
     * the application is used again instead of reporting both tools as missing.
     */
    private AppConfig withRepairedToolPaths(AppConfig config) {
        Path ytDlpPath = AppPaths.preferredToolPath(config.ytDlpPath(), AppPaths.ytDlpPath());
        Path ffmpegPath = AppPaths.preferredToolPath(config.ffmpegPath(), AppPaths.ffmpegPath());
        if (Objects.equals(ytDlpPath, config.ytDlpPath())
                && Objects.equals(ffmpegPath, config.ffmpegPath())) {
            return config;
        }
        logger.info("配置中的工具路径已失效，已回退到程序目录中的工具。");
        return new AppConfig(
                config.downloadDirectory(),
                config.maxConcurrentDownloads(),
                config.rateLimit(),
                config.proxy(),
                config.cookieFile(),
                config.cookieBrowser(),
                config.defaultFormatSelector(),
                ytDlpPath,
                ffmpegPath);
    }

    private YtDlpClient createYtDlpClient(AppConfig current) {
        return new DefaultYtDlpClient(
                current.ytDlpPath(),
                processExecutor,
                objectMapper,
                new DownloadOptions(null, current.proxy(), current.cookieFile(), current.ffmpegPath(),
                        current.cookieBrowser()));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
