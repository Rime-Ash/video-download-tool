package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DouyinVideoException;
import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.exception.ProcessExecutionException;
import com.nzsk.videodownloader.model.DouyinVideoInfo;
import com.nzsk.videodownloader.model.DouyinVideoRequest;
import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.DownloadState;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.util.BrowserLocator;
import com.nzsk.videodownloader.util.FileNameSanitizer;
import com.nzsk.videodownloader.util.MediaCdnHosts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Resolves and downloads a Douyin video by rendering its public page in a local headless browser.
 *
 * <p>Douyin refuses unsigned API requests with HTTP 403, which is why yt-dlp needs its "fresh cookies"
 * workaround and why this fallback exists. The browser is started with a throw-away profile directory, so
 * the user's own browser profile, history and cookies are neither read nor copied.</p>
 */
public final class DefaultDouyinVideoClient implements DouyinVideoClient, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDouyinVideoClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/141.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.douyin.com/";
    /** Time the page may keep running scripts before the DOM is dumped. */
    private static final long RENDER_BUDGET_MILLIS = 20_000L;
    private static final long RENDER_TIMEOUT_SECONDS = 90L;
    private static final long MAX_VIDEO_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long PROGRESS_STEP_BYTES = 512L * 1024L;
    private static final int MAX_BASE_NAME_LENGTH = 120;

    private final ProcessExecutor processExecutor;
    private final HttpClient httpClient;
    private final Path browserExecutable;
    private final Predicate<URI> mediaHostPolicy;
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "video-downloader-douyin");
        thread.setDaemon(true);
        return thread;
    });

    public DefaultDouyinVideoClient(ProcessExecutor processExecutor) {
        this(processExecutor,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                null,
                MediaCdnHosts::isAllowed);
    }

    /**
     * Injection point used by tests.
     *
     * @param browserExecutable browser to launch, or {@code null} to look it up when a link is inspected
     * @param mediaHostPolicy   which media hosts may be downloaded; production passes the CDN allowlist
     */
    DefaultDouyinVideoClient(ProcessExecutor processExecutor, HttpClient httpClient,
                             Path browserExecutable, Predicate<URI> mediaHostPolicy) {
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.browserExecutable = browserExecutable;
        this.mediaHostPolicy = Objects.requireNonNull(mediaHostPolicy, "mediaHostPolicy");
    }

    @Override
    public DouyinVideoInfo inspect(ValidatedUrl url, Path cookieFile) throws DouyinVideoException {
        Objects.requireNonNull(url, "url");
        String postId = DouyinPostIds.awemeId(url.uri())
                .orElseThrow(() -> new DouyinVideoException(
                        "无法从该链接中识别作品 ID，请使用作品的分享链接。"));
        Path browser = Optional.ofNullable(browserExecutable)
                .or(() -> BrowserLocator.locate())
                .orElseThrow(() -> new DouyinVideoException(
                        "未找到 Microsoft Edge 或 Google Chrome，无法使用浏览器兜底解析。"
                                + "请安装任一浏览器后重试。"));
        String html = render(browser, url.uri().toString());
        return DouyinVideoPageParser.parse(html, postId)
                .orElseThrow(() -> new DouyinVideoException(
                        "抖音页面没有返回可播放的地址：作品可能已删除、需要登录，或页面结构已变化。"
                                + "请确认能在浏览器中正常观看后重试。"));
    }

    @Override
    public DownloadHandle download(DouyinVideoRequest request, ProgressListener progressListener)
            throws DownloadException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progressListener, "progressListener");
        VideoDownloadHandle handle = new VideoDownloadHandle(request);
        workers.submit(() -> handle.run(progressListener));
        return handle;
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    private String render(Path browser, String url) throws DouyinVideoException {
        Path profileDirectory;
        try {
            profileDirectory = Files.createTempDirectory("video-downloader-browser-");
        } catch (IOException exception) {
            throw new DouyinVideoException("无法创建浏览器临时目录，请检查临时目录权限。", exception);
        }
        try {
            List<String> command = List.of(
                    browser.toString(),
                    "--headless=new",
                    "--disable-gpu",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-extensions",
                    "--disable-sync",
                    "--mute-audio",
                    "--user-data-dir=" + profileDirectory,
                    "--virtual-time-budget=" + RENDER_BUDGET_MILLIS,
                    "--dump-dom",
                    url);
            StringBuffer stdout = new StringBuffer();
            CountDownLatch outputCompleted = new CountDownLatch(2);
            ProcessOutputListener listener = new ProcessOutputListener() {
                @Override
                public void onLine(StreamType stream, String line) {
                    if (stream == StreamType.STDOUT) {
                        stdout.append(line).append(System.lineSeparator());
                    }
                }

                @Override
                public void onComplete(StreamType stream) {
                    outputCompleted.countDown();
                }
            };
            Process process = processExecutor.start(command, listener);
            if (!process.waitFor(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DouyinVideoException(
                        "浏览器渲染超时，请检查网络连接或代理设置后重试。");
            }
            outputCompleted.await(5, TimeUnit.SECONDS);
            return stdout.toString();
        } catch (ProcessExecutionException exception) {
            throw new DouyinVideoException("无法启动浏览器进行兜底解析，请确认浏览器安装完整。", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DouyinVideoException("浏览器渲染被中断。", exception);
        } finally {
            deleteRecursively(profileDirectory);
        }
    }

    private void deleteRecursively(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    LOGGER.debug("浏览器临时文件清理失败。");
                }
            });
        } catch (IOException exception) {
            LOGGER.debug("浏览器临时目录清理失败。");
        }
    }

    private final class VideoDownloadHandle implements DownloadHandle {
        private final DouyinVideoRequest request;
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile boolean cancelled;
        private volatile boolean succeeded;
        private volatile Path outputFile;
        private volatile String failureMessage;

        private VideoDownloadHandle(DouyinVideoRequest request) {
            this.request = request;
        }

        private void run(ProgressListener listener) {
            try {
                Path directory = request.downloadDirectory().toAbsolutePath().normalize();
                Files.createDirectories(directory);
                String baseName = FileNameSanitizer.sanitize(
                        request.baseFileName(), MAX_BASE_NAME_LENGTH);
                Path target = directory
                        .resolve(baseName + "." + request.format().extension())
                        .normalize();
                if (!target.startsWith(directory)) {
                    throw new DouyinVideoException("保存路径无效，已阻止目录穿越。");
                }
                if (Files.exists(target)) {
                    // Matches the yt-dlp "--no-overwrites" behaviour used by the normal video path.
                    outputFile = target;
                    succeeded = true;
                    return;
                }
                downloadTo(URI.create(request.format().url()), target, listener);
                outputFile = target;
                succeeded = true;
            } catch (DouyinVideoException exception) {
                failureMessage = exception.getMessage();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                if (!cancelled) {
                    failureMessage = "视频下载被中断。";
                }
            } catch (IOException exception) {
                failureMessage = "保存视频失败，请检查下载目录权限。";
            } catch (RuntimeException exception) {
                failureMessage = "视频下载失败，请稍后重试。";
                LOGGER.warn("抖音兜底下载出现未预期错误：{}", exception.getClass().getSimpleName());
            } finally {
                finished.countDown();
            }
        }

        private void downloadTo(URI uri, Path target, ProgressListener listener)
                throws DouyinVideoException, IOException, InterruptedException {
            if (!mediaHostPolicy.test(uri)) {
                throw new DouyinVideoException("视频地址不在允许的媒体 CDN 范围内，已停止下载。");
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMinutes(30))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", REFERER)
                    .header("Accept", "video/*,*/*;q=0.8")
                    .GET()
                    .build();
            Path temp = Files.createTempFile(target.getParent(), ".part-", ".tmp");
            try {
                HttpResponse<InputStream> response =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    throw new DouyinVideoException(
                            "视频下载失败，HTTP " + response.statusCode() + "。播放地址可能已过期，请重新解析。");
                }
                long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (total > MAX_VIDEO_BYTES) {
                    throw new DouyinVideoException("视频体积异常，已停止下载。");
                }
                long startedNanos = System.nanoTime();
                long written = 0L;
                long lastReported = 0L;
                byte[] buffer = new byte[64 * 1024];
                try (InputStream input = response.body();
                     OutputStream output = Files.newOutputStream(temp)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (cancelled) {
                            throw new InterruptedException("cancelled");
                        }
                        output.write(buffer, 0, read);
                        written += read;
                        if (written - lastReported >= PROGRESS_STEP_BYTES) {
                            lastReported = written;
                            listener.onProgress(progress(written, total, startedNanos));
                        }
                    }
                }
                if (written == 0L) {
                    throw new DouyinVideoException("视频内容为空，播放地址可能已过期，请重新解析。");
                }
                listener.onProgress(progress(written, written, startedNanos));
                move(temp, target);
                temp = null;
            } finally {
                if (temp != null) {
                    Files.deleteIfExists(temp);
                }
            }
        }

        private DownloadProgress progress(long written, long total, long startedNanos) {
            double percentage = total > 0 ? Math.min(100.0, written * 100.0 / total) : 0.0;
            String size = formatBytes(written) + (total > 0 ? " / " + formatBytes(total) : "");
            long elapsedMillis = Math.max(1L, (System.nanoTime() - startedNanos) / 1_000_000L);
            double bytesPerSecond = written * 1000.0 / elapsedMillis;
            String eta = total > written && bytesPerSecond > 0
                    ? formatSeconds((long) ((total - written) / bytesPerSecond))
                    : "";
            return new DownloadProgress(percentage, size, formatBytes((long) bytesPerSecond) + "/s", eta,
                    DownloadState.DOWNLOADING);
        }

        @Override
        public void pause() {
            cancelled = true;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public void awaitCompletion() throws InterruptedException {
            finished.await();
        }

        @Override
        public boolean succeeded() {
            return succeeded;
        }

        @Override
        public Optional<Path> outputFile() {
            return Optional.ofNullable(outputFile);
        }

        @Override
        public Optional<String> failureMessage() {
            return Optional.ofNullable(failureMessage);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String formatBytes(long bytes) {
        double megabytes = bytes / 1024.0 / 1024.0;
        if (megabytes < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", megabytes);
        }
        return String.format(Locale.ROOT, "%.2f GB", megabytes / 1024.0);
    }

    private static String formatSeconds(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        long hours = safeSeconds / 3600;
        long minutes = (safeSeconds % 3600) / 60;
        long remaining = safeSeconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remaining)
                : String.format(Locale.ROOT, "%d:%02d", minutes, remaining);
    }
}
