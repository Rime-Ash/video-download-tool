package com.nzsk.videodownloader.model;

import java.util.List;

public record VideoInfo(
        String title,
        String uploader,
        Long durationSeconds,
        String thumbnailUrl,
        List<FormatInfo> formats
) {
    public VideoInfo {
        formats = formats == null ? List.of() : List.copyOf(formats);
    }
}
