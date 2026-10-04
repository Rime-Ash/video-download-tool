package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.model.ImagePostRequest;

import java.util.Objects;

public final class ImagePostDownloadJob implements DownloadJob {
    private final ImagePostClient client;
    private final ImagePostRequest request;

    public ImagePostDownloadJob(ImagePostClient client, ImagePostRequest request) {
        this.client = Objects.requireNonNull(client, "client");
        this.request = Objects.requireNonNull(request, "request");
    }

    @Override
    public String fileName() {
        return request.title();
    }

    @Override
    public String summary() {
        return "图集 " + request.images().size() + " 张";
    }

    @Override
    public DownloadHandle start(ProgressListener progressListener) throws DownloadException {
        return client.download(request, progressListener);
    }
}
