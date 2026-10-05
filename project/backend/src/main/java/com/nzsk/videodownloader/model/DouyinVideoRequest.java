package com.nzsk.videodownloader.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable instruction to save one Douyin video rendition that was resolved through the browser fallback.
 *
 * @param baseFileName sanitized file name without extension; the extension comes from the chosen format
 */
public record DouyinVideoRequest(
        ValidatedUrl url,
        String postId,
        String baseFileName,
        DouyinVideoFormat format,
        Path downloadDirectory,
        Path cookieFile
) {
    public DouyinVideoRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(downloadDirectory, "downloadDirectory");
        if (baseFileName == null || baseFileName.isBlank()
                || baseFileName.contains("/") || baseFileName.contains("\\")) {
            throw new IllegalArgumentException("baseFileName must be a plain file name");
        }
    }
}
