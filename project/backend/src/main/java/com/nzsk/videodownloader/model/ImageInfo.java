package com.nzsk.videodownloader.model;

import java.util.Objects;

/**
 * One image of an image post.
 *
 * @param url       direct image URL on the platform CDN
 * @param extension file extension without the dot, for example {@code jpg}
 * @param livePhoto motion part of a live photo (动图), or {@code null} for a still image
 */
public record ImageInfo(
        String url,
        String extension,
        Integer width,
        Integer height,
        LivePhotoInfo livePhoto
) {
    public ImageInfo {
        Objects.requireNonNull(url, "url");
        extension = extension == null || extension.isBlank() ? "jpg" : extension;
    }

    /** Convenience constructor for a still image without a motion part. */
    public ImageInfo(String url, String extension, Integer width, Integer height) {
        this(url, extension, width, height, null);
    }

    public boolean hasLivePhoto() {
        return livePhoto != null;
    }

    public String resolution() {
        return width == null || height == null ? "未知" : width + "x" + height;
    }
}
