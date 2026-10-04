package com.nzsk.videodownloader.model;

import java.util.UUID;

public record DownloadTaskId(UUID value) {
    public static DownloadTaskId create() {
        return new DownloadTaskId(UUID.randomUUID());
    }
}
