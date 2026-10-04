package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.ImagePostRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;

import java.nio.file.Path;

/**
 * Inspects and downloads a single Douyin image post (图文/图集).
 */
public interface ImagePostClient {
    /**
     * @param cookieFile optional Netscape cookie file exported by the user; never logged
     * @return the image post metadata
     * @throws ImagePostException when the post is unavailable or the platform asks for verification
     */
    ImagePostInfo inspect(ValidatedUrl url, Path cookieFile) throws ImagePostException;

    DownloadHandle download(ImagePostRequest request, ProgressListener progressListener)
            throws DownloadException;
}
