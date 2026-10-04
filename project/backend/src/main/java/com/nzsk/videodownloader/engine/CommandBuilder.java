package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.model.DownloadOptions;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.util.FileNameSanitizer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds yt-dlp argument lists. Arguments are always passed as a list so that no shell
 * interpretation is possible.
 */
public final class CommandBuilder {
    private static final String PROGRESS_TEMPLATE =
            "download:%(progress._percent_str)s|%(progress.downloaded_bytes)s"
                    + "|%(progress.speed)s|%(progress.eta)s";
    private static final int MAX_BASE_NAME_LENGTH = 120;

    private CommandBuilder() {
    }

    public static List<String> inspectionCommand(Path ytDlpPath, String url) {
        return inspectionCommand(ytDlpPath, url, DownloadOptions.NONE);
    }

    public static List<String> inspectionCommand(Path ytDlpPath, String url, DownloadOptions options) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(ytDlpPath, "ytDlpPath");
        List<String> command = new ArrayList<>();
        command.add(ytDlpPath.toString());
        command.add("-J");
        command.add("--no-playlist");
        command.add("--no-warnings");
        appendNetworkOptions(command, options);
        command.add(url);
        return List.copyOf(command);
    }

    public static List<String> downloadCommand(Path ytDlpPath, DownloadRequest request) {
        Objects.requireNonNull(ytDlpPath, "ytDlpPath");
        Objects.requireNonNull(request, "request");
        Path baseDirectory = request.downloadDirectory().toAbsolutePath().normalize();
        String safeBaseName = FileNameSanitizer.sanitize(request.baseFileName(), MAX_BASE_NAME_LENGTH);
        Path outputPath = baseDirectory.resolve(safeBaseName + ".%(ext)s").normalize();
        if (!outputPath.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("outputTemplate escapes the download directory");
        }

        List<String> command = new ArrayList<>();
        command.add(ytDlpPath.toString());
        command.add("--newline");
        command.add("--no-playlist");
        command.add("--windows-filenames");
        command.add("--trim-filenames");
        command.add("150");
        command.add("--no-overwrites");
        if (request.continueDownload()) {
            command.add("--continue");
        }
        command.add("--progress-template");
        command.add(PROGRESS_TEMPLATE);
        command.add("-f");
        command.add(request.formatSelector());
        command.add("-o");
        command.add(outputPath.toString());
        appendDownloadOptions(command, request.options());
        command.add(request.url().uri().toString());
        return List.copyOf(command);
    }

    private static void appendDownloadOptions(List<String> command, DownloadOptions options) {
        DownloadOptions effective = options == null ? DownloadOptions.NONE : options;
        if (effective.ffmpegPath() != null) {
            command.add("--ffmpeg-location");
            command.add(effective.ffmpegPath().toString());
        }
        if (effective.rateLimit() != null) {
            command.add("--limit-rate");
            command.add(effective.rateLimit());
        }
        appendNetworkOptions(command, effective);
    }

    private static void appendNetworkOptions(List<String> command, DownloadOptions options) {
        DownloadOptions effective = options == null ? DownloadOptions.NONE : options;
        if (effective.proxy() != null) {
            command.add("--proxy");
            command.add(effective.proxy().toString());
        }
        // An explicit exported cookie file wins: it is a deliberate user action, while the browser
        // profile can be unreadable (browser running, app-bound encryption).
        if (effective.cookieFile() != null) {
            command.add("--cookies");
            command.add(effective.cookieFile().toString());
        } else if (effective.cookieBrowser() != null) {
            command.add("--cookies-from-browser");
            command.add(effective.cookieBrowser());
        }
    }
}
