package com.nzsk.videodownloader.model;

import java.nio.file.Path;

public record DownloadTask(
        DownloadTaskId id,
        DownloadTaskKind kind,
        String fileName,
        String summary,
        DownloadState state,
        DownloadProgress progress,
        Path outputFile,
        String errorMessage
) {
}
