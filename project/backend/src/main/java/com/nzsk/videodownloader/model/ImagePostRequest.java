package com.nzsk.videodownloader.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable instruction to save every image of one image post into its own folder.
 */
public record ImagePostRequest(
        ValidatedUrl url,
        String postId,
        String title,
        List<ImageInfo> images,
        Path downloadDirectory,
        Path cookieFile
) {
    public ImagePostRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(downloadDirectory, "downloadDirectory");
        title = title == null || title.isBlank() ? "douyin-" + postId : title;
        images = images == null ? List.of() : List.copyOf(images);
    }
}
