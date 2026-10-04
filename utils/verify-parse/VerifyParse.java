import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.engine.DefaultImagePostClient;
import com.nzsk.videodownloader.engine.DefaultProcessExecutor;
import com.nzsk.videodownloader.engine.DefaultYtDlpClient;
import com.nzsk.videodownloader.engine.DouyinPostIds;
import com.nzsk.videodownloader.engine.YtDlpClient;
import com.nzsk.videodownloader.model.DownloadOptions;
import com.nzsk.videodownloader.model.FormatInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.ImagePostRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.model.VideoInfo;
import com.nzsk.videodownloader.service.HttpRedirectResolver;
import com.nzsk.videodownloader.util.AppPaths;
import com.nzsk.videodownloader.util.DefaultPathSecurity;
import com.nzsk.videodownloader.util.DefaultUrlValidator;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Development harness that runs the application code path (URL validation, normalisation, video inspection
 * or image post inspection, and an optional image download) without opening the JavaFX window.
 *
 * <p>Usage: {@code VerifyParse <url> [cookieFile|-] [cookieBrowser|-] [downloadDirectory|-]}</p>
 */
public final class VerifyParse {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("用法: VerifyParse <url> [cookieFile|-] [cookieBrowser|-] [downloadDirectory|-]");
            return;
        }
        String rawUrl = args[0];
        Path cookieFile = arg(args, 1);
        String cookieBrowser = args.length > 2 && !"-".equals(args[2]) ? args[2] : null;
        Path downloadDirectory = arg(args, 3);

        var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var validator = new DefaultUrlValidator(new HttpRedirectResolver(httpClient, 5));
        System.out.println("yt-dlp     : " + AppPaths.ytDlpPath());
        System.out.println("ffmpeg     : " + AppPaths.ffmpegPath());
        System.out.println("cookie     : file=" + cookieFile + " browser=" + cookieBrowser);

        ValidatedUrl validated;
        try {
            validated = validator.validate(rawUrl);
        } catch (Exception exception) {
            System.out.println("URL 校验结果 : 拒绝 -> " + exception.getMessage());
            return;
        }
        System.out.println("URL 校验结果 : 通过");
        System.out.println("  规范化后   : " + validated.uri());
        System.out.println("  最终域名   : " + validated.host());

        if (DouyinPostIds.isImagePostPath(validated.uri())) {
            inspectImagePost(validated, cookieFile, downloadDirectory);
        } else {
            inspectVideo(validated, cookieFile, cookieBrowser);
        }
    }

    private static void inspectImagePost(ValidatedUrl url, Path cookieFile, Path downloadDirectory)
            throws Exception {
        try (var client = new DefaultImagePostClient(new ObjectMapper())) {
            ImagePostInfo info;
            try {
                info = client.inspect(url, cookieFile);
            } catch (Exception exception) {
                System.out.println("解析结果     : 失败");
                System.out.println("  界面提示   : " + exception.getMessage());
                return;
            }
            System.out.println("解析结果     : 成功（图文作品）");
            System.out.println("  标题       : " + info.title());
            System.out.println("  作者       : " + info.author());
            System.out.println("  图片数量   : " + info.imageCount());
            info.images().stream().limit(5).forEach(image -> System.out.println(
                    "    " + image.extension() + " " + image.resolution()));

            if (downloadDirectory == null) {
                System.out.println("（未指定下载目录，仅解析）");
                return;
            }
            Path folderBase = new DefaultPathSecurity()
                    .resolveUniqueBaseName(downloadDirectory, info.title());
            ImagePostRequest request = new ImagePostRequest(url, info.postId(),
                    folderBase.getFileName().toString(), info.images(), downloadDirectory, cookieFile);
            var handle = client.download(request, progress -> System.out.println(
                    "  进度       : " + Math.round(progress.percentage()) + "% "
                            + progress.downloadedSize()));
            handle.awaitCompletion();
            System.out.println("下载结果     : " + (handle.succeeded() ? "成功" : "失败"));
            handle.failureMessage().ifPresent(message -> System.out.println("  失败原因   : " + message));
            handle.outputFile().ifPresent(folder -> {
                System.out.println("  保存目录   : " + folder);
                try (var files = Files.list(folder)) {
                    files.sorted().forEach(file -> System.out.println(
                            "    " + file.getFileName() + " (" + size(file) + " 字节)"));
                } catch (Exception exception) {
                    System.out.println("    目录读取失败：" + exception.getMessage());
                }
            });
        }
    }

    private static void inspectVideo(ValidatedUrl url, Path cookieFile, String cookieBrowser)
            throws Exception {
        var options = new DownloadOptions(null, null, cookieFile, AppPaths.ffmpegPath(), cookieBrowser);
        try (var processExecutor = new DefaultProcessExecutor()) {
            YtDlpClient client = new DefaultYtDlpClient(
                    AppPaths.ytDlpPath(), processExecutor, new ObjectMapper(), options);
            VideoInfo info;
            try {
                info = client.inspect(url);
            } catch (Exception exception) {
                System.out.println("解析结果     : 失败");
                System.out.println("  界面提示   : " + exception.getMessage());
                return;
            }
            System.out.println("解析结果     : 成功（视频）");
            System.out.println("  标题       : " + info.title());
            System.out.println("  作者       : " + info.uploader());
            System.out.println("  时长(秒)   : " + info.durationSeconds());
            System.out.println("  封面       : " + (info.thumbnailUrl() == null ? "无" : "有"));
            System.out.println("  格式数量   : " + info.formats().size());
            info.formats().stream()
                    .sorted(Comparator.comparingInt(
                            (FormatInfo format) -> format.height() == null ? -1 : format.height()).reversed())
                    .limit(4)
                    .forEach(format -> System.out.println("    " + format.formatId() + " " + format.extension()
                            + " " + format.height() + "p v=" + format.videoCodec()
                            + " a=" + format.audioCodec()));
        }
    }

    private static Path arg(String[] args, int index) {
        if (args.length <= index || args[index] == null || args[index].isBlank()
                || "-".equals(args[index])) {
            return null;
        }
        return Path.of(args[index]);
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (Exception exception) {
            return -1;
        }
    }
}
