package com.nzsk.videodownloader.model;

import java.util.Objects;

/**
 * One playable rendition of a Douyin video resolved by the browser fallback.
 *
 * <p>The Douyin web page exposes ready-to-play media URLs but does not describe them with the detail
 * metadata yt-dlp reports (resolution, codecs, file size), so only the fields the page really offers are
 * kept.</p>
 *
 * @param id        short label shown in the format column
 * @param url       direct media URL on the platform CDN
 * @param extension file extension without the dot; video renditions are MP4
 * @param note      short explanation shown in the format table
 */
public record DouyinVideoFormat(
        String id,
        String url,
        String extension,
        String note
) {
    public DouyinVideoFormat {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
        extension = extension == null || extension.isBlank() ? "mp4" : extension;
    }
}
