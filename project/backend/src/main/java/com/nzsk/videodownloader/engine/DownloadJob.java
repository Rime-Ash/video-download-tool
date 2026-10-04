package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DownloadException;

/**
 * One unit of work inside the download queue. Videos and image posts are both described by this
 * contract, so the queue can treat them identically (queueing, progress, pause, cancel, retry).
 */
public interface DownloadJob {
    /** Name shown in the file name column. */
    String fileName();

    /** Short description shown in the quality column, for example a format selector or "图集 9 张". */
    String summary();

    DownloadHandle start(ProgressListener progressListener) throws DownloadException;
}
