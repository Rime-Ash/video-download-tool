package com.nzsk.videodownloader.model;

import java.util.List;
import java.util.Objects;

/**
 * Metadata of a single Douyin image post (图文/图集). Only the images of that one post are described;
 * account pages, playlists and bulk collection stay out of scope.
 */
public record ImagePostInfo(
        String postId,
        String title,
        String author,
        List<ImageInfo> images
) {
    public ImagePostInfo {
        Objects.requireNonNull(postId, "postId");
        images = images == null ? List.of() : List.copyOf(images);
    }

    public int imageCount() {
        return images.size();
    }
}
