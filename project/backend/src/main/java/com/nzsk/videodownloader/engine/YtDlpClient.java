package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.exception.ProcessExecutionException;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.model.VideoInfo;

public interface YtDlpClient {
    VideoInfo inspect(ValidatedUrl url) throws ProcessExecutionException;

    DownloadHandle download(
            DownloadRequest request,
            ProgressListener progressListener
    ) throws DownloadException;
}
