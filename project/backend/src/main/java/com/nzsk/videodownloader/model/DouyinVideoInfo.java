package com.nzsk.videodownloader.model;

import java.util.List;
import java.util.Objects;

/**
 * Metadata of one Douyin video resolved without yt-dlp.
 *
 * <p>Only the information the rendered page provides is kept: title, author, cover and the playable media
 * addresses. Duration stays {@code null} because the page does not expose it before playing.</p>
 */
public record DouyinVideoInfo(
        String postId,
        String title,
        String author,
        Long durationSeconds,
        String thumbnailUrl,
        List<DouyinVideoFormat> formats
) {
    public DouyinVideoInfo {
        Objects.requireNonNull(postId, "postId");
        formats = formats == null ? List.of() : List.copyOf(formats);
    }

    public boolean hasFormats() {
        return !formats.isEmpty();
    }
}
