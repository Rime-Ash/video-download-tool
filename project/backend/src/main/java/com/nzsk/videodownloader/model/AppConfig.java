package com.nzsk.videodownloader.model;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

public record AppConfig(
        Path downloadDirectory,
        int maxConcurrentDownloads,
        String rateLimit,
        URI proxy,
        Path cookieFile,
        String cookieBrowser,
        String defaultFormatSelector,
        Path ytDlpPath,
        Path ffmpegPath
) {
    public AppConfig {
        Objects.requireNonNull(downloadDirectory, "downloadDirectory");
        Objects.requireNonNull(defaultFormatSelector, "defaultFormatSelector");
        Objects.requireNonNull(ytDlpPath, "ytDlpPath");
        Objects.requireNonNull(ffmpegPath, "ffmpegPath");
        if (maxConcurrentDownloads < 1) {
            throw new IllegalArgumentException("maxConcurrentDownloads must be positive");
        }
    }
}
