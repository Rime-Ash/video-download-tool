package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.model.DownloadProgress;

@FunctionalInterface
public interface ProgressListener {
    void onProgress(DownloadProgress progress);
}
