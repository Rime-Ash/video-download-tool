package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.DownloadTask;
import com.nzsk.videodownloader.model.DownloadTaskId;
import com.nzsk.videodownloader.model.ImagePostRequest;

public interface DownloadService {
    DownloadTask addTask(DownloadRequest request);

    DownloadTask addImagePostTask(ImagePostRequest request);

    void start(DownloadTaskId taskId);

    void pause(DownloadTaskId taskId);

    void resume(DownloadTaskId taskId);

    void cancel(DownloadTaskId taskId);

    void retry(DownloadTaskId taskId);

    void removeCompletedTasks();

    /**
     * Applies the configured concurrency limit. Queued tasks pick the new limit up immediately.
     */
    void setMaxConcurrentDownloads(int maxConcurrentDownloads);
}
