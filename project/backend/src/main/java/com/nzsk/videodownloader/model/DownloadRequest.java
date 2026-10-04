package com.nzsk.videodownloader.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable download instruction.
 *
 * @param baseFileName sanitized file name without extension; the extension is filled in by yt-dlp
 */
public record DownloadRequest(
        ValidatedUrl url,
        String formatSelector,
        Path downloadDirectory,
        String baseFileName,
        boolean continueDownload,
        DownloadOptions options
) {
    public DownloadRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(formatSelector, "formatSelector");
        Objects.requireNonNull(downloadDirectory, "downloadDirectory");
        Objects.requireNonNull(baseFileName, "baseFileName");
        if (formatSelector.isBlank()) {
            throw new IllegalArgumentException("formatSelector must not be blank");
        }
        if (baseFileName.isBlank() || baseFileName.contains("/") || baseFileName.contains("\\")) {
            throw new IllegalArgumentException("baseFileName must be a plain file name");
        }
        options = options == null ? DownloadOptions.NONE : options;
    }
}
