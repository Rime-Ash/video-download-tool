package com.nzsk.videodownloader.model;

/** What kind of media a queue entry represents. */
public enum DownloadTaskKind {
    VIDEO,
    IMAGE_POST,
    /** Douyin video resolved through the browser fallback instead of yt-dlp. */
    DOUYIN_VIDEO
}
