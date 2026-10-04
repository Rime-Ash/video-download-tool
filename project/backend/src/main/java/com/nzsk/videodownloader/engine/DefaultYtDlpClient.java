package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.exception.ProcessExecutionException;
import com.nzsk.videodownloader.model.DownloadOptions;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.model.VideoInfo;
import com.nzsk.videodownloader.service.ProgressParser;
import com.nzsk.videodownloader.util.DownloadedFileLocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DefaultYtDlpClient implements YtDlpClient {
    private final Path executable;
    private final ProcessExecutor processExecutor;
    private final YtDlpJsonParser jsonParser;
    private final DownloadOptions inspectionOptions;

    public DefaultYtDlpClient(Path executable, ProcessExecutor processExecutor, ObjectMapper objectMapper) {
        this(executable, processExecutor, objectMapper, DownloadOptions.NONE);
    }

    public DefaultYtDlpClient(Path executable, ProcessExecutor processExecutor, ObjectMapper objectMapper,
                              DownloadOptions inspectionOptions) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
        this.jsonParser = new YtDlpJsonParser(Objects.requireNonNull(objectMapper, "objectMapper"));
        this.inspectionOptions = inspectionOptions == null ? DownloadOptions.NONE : inspectionOptions;
    }

    @Override
    public VideoInfo inspect(ValidatedUrl url) throws ProcessExecutionException {
        Objects.requireNonNull(url, "url");
        StringBuffer stdout = new StringBuffer();
        StringBuffer stderr = new StringBuffer();
        CountDownLatch outputCompleted = new CountDownLatch(2);
        ProcessOutputListener listener = new ProcessOutputListener() {
            @Override
            public void onLine(StreamType stream, String line) {
                if (stream == StreamType.STDOUT) {
                    stdout.append(line).append(System.lineSeparator());
                } else {
                    stderr.append(line).append(System.lineSeparator());
                }
            }

            @Override
            public void onComplete(StreamType stream) {
                outputCompleted.countDown();
            }
        };
        Process process = processExecutor.start(
                CommandBuilder.inspectionCommand(executable, url.uri().toString(), inspectionOptions), listener);
        waitForCompletion(process, 60);
        awaitOutput(outputCompleted);
        if (process.exitValue() != 0) {
            throw new ProcessExecutionException("视频解析失败：" + hintFor(stderr.toString()));
        }
        try {
            return jsonParser.parse(stdout.toString());
        } catch (IOException exception) {
            throw new ProcessExecutionException("视频解析结果格式无效，请更新 yt-dlp 后重试。", exception);
        }
    }

    @Override
    public DownloadHandle download(DownloadRequest request, ProgressListener progressListener)
            throws DownloadException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progressListener, "progressListener");
        try {
            Process process = processExecutor.start(
                    CommandBuilder.downloadCommand(executable, request),
                    (stream, line) -> {
                        if (stream == ProcessOutputListener.StreamType.STDOUT) {
                            ProgressParser.parse(line).ifPresent(progressListener::onProgress);
                        }
                    }
            );
            return new DefaultDownloadHandle(process, () -> DownloadedFileLocator
                    .find(request.downloadDirectory(), request.baseFileName())
                    .orElse(null));
        } catch (ProcessExecutionException exception) {
            throw new DownloadException("无法启动下载，请检查 yt-dlp 路径和文件权限。", exception);
        }
    }

    private void waitForCompletion(Process process, long timeoutSeconds)
            throws ProcessExecutionException {
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ProcessExecutionException("视频解析超时，请检查网络连接后重试。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException("视频解析被中断。", exception);
        }
    }

    private void awaitOutput(CountDownLatch outputCompleted) throws ProcessExecutionException {
        try {
            if (!outputCompleted.await(5, TimeUnit.SECONDS)) {
                throw new ProcessExecutionException("读取 yt-dlp 输出超时。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProcessExecutionException("读取 yt-dlp 输出被中断。", exception);
        }
    }

    /**
     * Maps yt-dlp output to an actionable hint. The raw output is never shown or logged because it can
     * contain cookie paths and account specific content.
     */
    static String hintFor(String ytDlpOutput) {
        String output = ytDlpOutput == null ? "" : ytDlpOutput.toLowerCase(java.util.Locale.ROOT);
        if (output.contains("failed to decrypt") || output.contains("dpapi")
                || output.contains("app_bound") || output.contains("app-bound")) {
            return "无法读取浏览器 Cookie：该浏览器使用应用绑定加密（Chromium 127 及以上），"
                    + "yt-dlp 无法直接解密。请改用浏览器扩展导出 Netscape 格式的 Cookie 文件，"
                    + "并在“设置”中选择该文件；或改用 Firefox 浏览器的 Cookie。";
        }
        if (output.contains("could not copy") || output.contains("database is locked")
                || output.contains("unable to open database")) {
            return "浏览器正在占用 Cookie 数据库。请完全退出浏览器（包括后台进程）后重试，"
                    + "或改用导出的 Cookie 文件。";
        }
        if (output.contains("cookies") || output.contains("sign in") || output.contains("log in")
                || output.contains("login")) {
            return "该站点需要有效的浏览器 Cookie，或该视频当前不可访问。请依次确认："
                    + "①在“设置”中用浏览器扩展导出的 Netscape 格式 Cookie 文件（浏览器 Cookie 来源请选“不使用”）；"
                    + "②链接来自视频播放页的“分享 → 复制链接”，而不是账号主页；"
                    + "③该视频未删除、不是图文帖，且你能在浏览器里正常观看。";
        }
        if (output.contains("unsupported url")) {
            return "该链接不是单个视频页面。请在视频播放页使用“分享 → 复制链接”，再粘贴得到的链接。";
        }
        if (output.contains("unable to extract") || output.contains("failed to parse")
                || output.contains("unable to download webpage")) {
            return "平台返回的数据无法解析，通常是 yt-dlp 需要更新，或该视频需要登录 Cookie。"
                    + "请更新 yt-dlp.exe 或在“设置”中配置 Cookie 后重试。";
        }
        if (output.contains("timed out") || output.contains("connection")
                || output.contains("network")) {
            return "网络连接超时，请检查网络连接、代理设置后重试。";
        }
        return "请确认链接可以正常访问，必要时在“设置”中配置 Cookie 或更新 yt-dlp。";
    }
}
