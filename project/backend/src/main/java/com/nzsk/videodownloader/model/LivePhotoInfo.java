package com.nzsk.videodownloader.model;

import java.util.Objects;

/**
 * The short video attached to a live photo (实况/动图) inside an image post.
 *
 * @param videoUrl       direct video URL on the platform media CDN
 * @param durationMillis length of the motion part in milliseconds
 */
public record LivePhotoInfo(
        String videoUrl,
        long durationMillis,
        Integer width,
        Integer height
) {
    public LivePhotoInfo {
        Objects.requireNonNull(videoUrl, "videoUrl");
    }

    /** Duration in seconds with one decimal, used by the user interface. */
    public String durationText() {
        return String.format(java.util.Locale.ROOT, "%.1f 秒", durationMillis / 1000.0);
    }
}
