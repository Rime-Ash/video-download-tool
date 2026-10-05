package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.model.DouyinVideoRequest;

import java.util.Objects;

/** One Douyin video rendition resolved by the browser fallback, queued like any other download. */
public final class DouyinVideoDownloadJob implements DownloadJob {
    private final DouyinVideoClient client;
    private final DouyinVideoRequest request;

    public DouyinVideoDownloadJob(DouyinVideoClient client, DouyinVideoRequest request) {
        this.client = Objects.requireNonNull(client, "client");
        this.request = Objects.requireNonNull(request, "request");
    }

    @Override
    public String fileName() {
        return request.baseFileName() + "." + request.format().extension();
    }

    @Override
    public String summary() {
        return request.format().note();
    }

    @Override
    public DownloadHandle start(ProgressListener progressListener) throws DownloadException {
        return client.download(request, progressListener);
    }
}
