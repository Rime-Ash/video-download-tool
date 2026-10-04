package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.model.DownloadRequest;

import java.util.Objects;

public final class VideoDownloadJob implements DownloadJob {
    private final YtDlpClient client;
    private final DownloadRequest request;

    public VideoDownloadJob(YtDlpClient client, DownloadRequest request) {
        this.client = Objects.requireNonNull(client, "client");
        this.request = Objects.requireNonNull(request, "request");
    }

    @Override
    public String fileName() {
        return request.baseFileName();
    }

    @Override
    public String summary() {
        return request.formatSelector();
    }

    @Override
    public DownloadHandle start(ProgressListener progressListener) throws DownloadException {
        return client.download(request, progressListener);
    }
}
