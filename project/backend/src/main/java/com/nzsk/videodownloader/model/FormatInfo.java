package com.nzsk.videodownloader.model;

public record FormatInfo(
        String formatId,
        String extension,
        String videoCodec,
        String audioCodec,
        Integer width,
        Integer height,
        Long fileSize,
        String note
) {
}
