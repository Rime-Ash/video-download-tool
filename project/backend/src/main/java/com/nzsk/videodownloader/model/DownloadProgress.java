package com.nzsk.videodownloader.model;

public record DownloadProgress(
        double percentage,
        String downloadedSize,
        String speed,
        String eta,
        DownloadState state
) {
    public DownloadProgress {
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("percentage must be between 0 and 100");
        }
    }
}
