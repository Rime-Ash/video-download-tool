package com.nzsk.videodownloader.model;

public enum DownloadState {
    QUEUED,
    PARSING,
    DOWNLOADING,
    PAUSED,
    MERGING,
    COMPLETED,
    FAILED,
    CANCELLED
}
