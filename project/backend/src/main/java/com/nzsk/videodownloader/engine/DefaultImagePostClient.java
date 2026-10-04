package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.DownloadState;
import com.nzsk.videodownloader.model.ImageInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.ImagePostRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.util.FileNameSanitizer;
import com.nzsk.videodownloader.util.ImageCdnHosts;
import com.nzsk.videodownloader.util.NetscapeCookieStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Inspects and downloads single Douyin image posts through the platform web API.
 *
 * <p>Cookie values are only copied into the request header for douyin.com and its media CDNs; they are never
 * logged. Image URLs are validated against the CDN allowlist before they are fetched, and files are written
 * through a temporary {@code .part} file inside the target folder.</p>
 */
public final class DefaultImagePostClient implements ImagePostClient, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultImagePostClient.class);

    private static final String DETAIL_ENDPOINT = "https://www.douyin.com/aweme/v1/web/aweme/detail/";
    private static final String WEB_PARAMS = "device_platform=webapp&aid=6383&channel=channel_pc_web"
            + "&pc_client_type=1&version_code=190500&version_name=19.5.0&cookie_enabled=true"
            + "&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32"
            + "&browser_name=Chrome&browser_version=141.0.0.0&browser_online=true&engine_name=Blink"
            + "&engine_version=141.0.0.0&os_name=Windows&os_version=10&cpu_core_num=8&device_memory=8"
            + "&platform=PC&downlink=10&effective_type=4g&round_trip_time=50";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/141.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.douyin.com/";
    private static final long MAX_IMAGE_BYTES = 60L * 1024L * 1024L;
    private static final int MAX_FOLDER_NAME_LENGTH = 100;
    private static final int CHALLENGE_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 1_500L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String detailEndpoint;
    private final java.util.function.Predicate<URI> imageHostPolicy;
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "video-downloader-image");
        thread.setDaemon(true);
        return thread;
    });

    public DefaultImagePostClient(ObjectMapper objectMapper) {
        this(objectMapper,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15))
                        .build(),
                DETAIL_ENDPOINT,
                ImageCdnHosts::isAllowed);
    }

    /**
     * Injection point used by tests to point the client at a local server. Production always uses the
     * Douyin endpoint and the media CDN allowlist.
     */
    DefaultImagePostClient(ObjectMapper objectMapper, HttpClient httpClient, String detailEndpoint,
                           java.util.function.Predicate<URI> imageHostPolicy) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.detailEndpoint = Objects.requireNonNull(detailEndpoint, "detailEndpoint");
        this.imageHostPolicy = Objects.requireNonNull(imageHostPolicy, "imageHostPolicy");
    }

    @Override
    public ImagePostInfo inspect(ValidatedUrl url, Path cookieFile) throws ImagePostException {
        String postId = DouyinPostIds.awemeId(url.uri())
                .orElseThrow(() -> new ImagePostException("无法从该链接中识别作品 ID，请使用作品的分享链接。"));
        ImagePostException lastFailure = null;
        for (int attempt = 1; attempt <= CHALLENGE_ATTEMPTS; attempt++) {
            HttpResponse<String> response = send(apiRequest(postId, cookieFile),
                    HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() != 200) {
                lastFailure = new ImagePostException(
                        "图集接口返回 HTTP " + response.statusCode() + "，请稍后重试。");
            } else if (body == null || body.isBlank() || looksLikeChallenge(body)) {
                // Douyin intermittently answers with a captcha interstitial. Retrying the very same request
                // usually succeeds and never solves or bypasses the verification.
                lastFailure = new ImagePostException("抖音要求安全验证。请在浏览器中打开该作品确认能正常观看，"
                        + "并确认“设置”中的 Cookie 文件是最新导出的，然后重试。");
            } else {
                Optional<ImagePostInfo> parsed =
                        DouyinImagePostParser.parse(body, objectMapper, imageHostPolicy);
                if (parsed.isPresent()) {
                    return parsed.get();
                }
                throw new ImagePostException(describeEmptyResult(body, postId));
            }
            if (attempt < CHALLENGE_ATTEMPTS && !sleepBeforeRetry(attempt)) {
                break;
            }
        }
        throw lastFailure == null ? new ImagePostException("图集解析失败，请稍后重试。") : lastFailure;
    }

    private static boolean looksLikeChallenge(String body) {
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            return false;
        }
        return trimmed.toLowerCase(Locale.ROOT).contains("captcha") || trimmed.contains("验证码");
    }

    private boolean sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public DownloadHandle download(ImagePostRequest request, ProgressListener progressListener)
            throws DownloadException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progressListener, "progressListener");
        if (request.images().isEmpty()) {
            throw new DownloadException("该图集没有可下载的图片。");
        }
        ImageDownloadHandle handle = new ImageDownloadHandle(request);
        workers.submit(() -> handle.run(progressListener));
        return handle;
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    private String describeEmptyResult(String body, String postId) {
        try {
            JsonNode detail = objectMapper.readTree(body).path("aweme_detail");
            if (detail.isMissingNode() || detail.isNull()) {
                return "抖音没有返回该作品的数据：作品可能已删除、设为私密，或需要登录后才能查看。";
            }
            int awemeType = detail.path("aweme_type").asInt(0);
            if (awemeType != 68) {
                return "该链接是视频作品（不是图集），请在“下载”页解析后按视频下载。";
            }
        } catch (IOException exception) {
            LOGGER.debug("图集接口响应无法解析为 JSON。");
        }
        return "作品 " + postId + " 没有可下载的图片。";
    }

    private HttpRequest apiRequest(String postId, Path cookieFile) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create(detailEndpoint + "?" + WEB_PARAMS + "&aweme_id=" + postId))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Accept", "application/json, text/plain, */*")
                .GET();
        NetscapeCookieStore.cookieHeader(cookieFile, URI.create(REFERER))
                .ifPresent(header -> builder.header("Cookie", header));
        return builder.build();
    }

    private HttpRequest imageRequest(String imageUrl, Path cookieFile) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(imageUrl))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
                .GET();
        NetscapeCookieStore.cookieHeader(cookieFile, URI.create(REFERER))
                .ifPresent(header -> builder.header("Cookie", header));
        return builder.build();
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws ImagePostException {
        try {
            return httpClient.send(request, handler);
        } catch (IOException exception) {
            throw new ImagePostException("无法连接抖音，请检查网络连接或代理设置。", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImagePostException("操作被中断。", exception);
        }
    }

    private Path resolveFolder(ImagePostRequest request) throws ImagePostException {
        Path base = request.downloadDirectory().toAbsolutePath().normalize();
        String safeName = FileNameSanitizer.sanitize(request.title(), MAX_FOLDER_NAME_LENGTH);
        Path folder = base.resolve(safeName).normalize();
        if (!folder.startsWith(base)) {
            throw new ImagePostException("保存路径无效，已阻止目录穿越。");
        }
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            throw new ImagePostException("无法创建图集保存目录，请检查下载目录权限。", exception);
        }
        return folder;
    }

    private void downloadOne(String imageUrl, Path target, Path cookieFile) throws ImagePostException {
        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            throw new ImagePostException("图集返回了无效的图片地址。", exception);
        }
        if (!imageHostPolicy.test(uri)) {
            throw new ImagePostException("图片地址不在允许的媒体 CDN 范围内，已停止下载。");
        }
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), ".part-", ".tmp");
            HttpResponse<Path> response = httpClient.send(
                    imageRequest(imageUrl, cookieFile), HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                throw new ImagePostException("图片下载失败，HTTP " + response.statusCode() + "。");
            }
            if (Files.size(temp) > MAX_IMAGE_BYTES) {
                throw new ImagePostException("单张图片超过 60 MB，已跳过以保证稳定。");
            }
            if (Files.size(temp) == 0L) {
                throw new ImagePostException("图片内容为空，可能链接已过期。");
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException exception) {
            throw new ImagePostException("保存图片失败，请检查下载目录权限。", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImagePostException("图片下载被中断。", exception);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    LOGGER.debug("临时文件清理失败。");
                }
            }
        }
    }

    private final class ImageDownloadHandle implements DownloadHandle {
        private final ImagePostRequest request;
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile boolean cancelled;
        private volatile boolean succeeded;
        private volatile Path outputFolder;
        private volatile String failureMessage;

        private ImageDownloadHandle(ImagePostRequest request) {
            this.request = request;
        }

        private void run(ProgressListener listener) {
            try {
                Path folder = resolveFolder(request);
                List<ImageInfo> images = request.images();
                int total = images.size();
                int finishedImages = 0;
                for (int index = 0; index < total; index++) {
                    if (cancelled) {
                        return;
                    }
                    ImageInfo image = images.get(index);
                    Path target = folder.resolve(String.format(Locale.ROOT, "%02d.%s",
                            index + 1, image.extension()));
                    if (!Files.exists(target)) {
                        downloadOne(image.url(), target, request.cookieFile());
                    }
                    // Live photos (动图/实况) also carry a short video; save it next to the still image.
                    if (image.hasLivePhoto()) {
                        Path videoTarget = folder.resolve(String.format(Locale.ROOT, "%02d.mp4",
                                index + 1));
                        if (!Files.exists(videoTarget)) {
                            downloadOne(image.livePhoto().videoUrl(), videoTarget, request.cookieFile());
                        }
                    }
                    finishedImages++;
                    listener.onProgress(new DownloadProgress(
                            finishedImages * 100.0 / total,
                            finishedImages + " / " + total + " 张",
                            "", "",
                            DownloadState.DOWNLOADING));
                }
                outputFolder = folder;
                succeeded = true;
            } catch (ImagePostException exception) {
                failureMessage = exception.getMessage();
            } catch (RuntimeException exception) {
                failureMessage = "图集下载失败，请稍后重试。";
                LOGGER.warn("图集下载出现未预期错误：{}", exception.getClass().getSimpleName());
            } finally {
                finished.countDown();
            }
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
            return Optional.ofNullable(outputFolder);
        }

        @Override
        public Optional<String> failureMessage() {
            return Optional.ofNullable(failureMessage);
        }
    }
}
