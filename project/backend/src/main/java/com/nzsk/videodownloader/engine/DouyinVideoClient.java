package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DouyinVideoException;
import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.model.DouyinVideoInfo;
import com.nzsk.videodownloader.model.DouyinVideoRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;

import java.nio.file.Path;

/**
 * Fallback used when yt-dlp cannot read a Douyin video.
 *
 * <p>Douyin rejects plain API requests that are not signed by its own web JavaScript, so this path renders
 * the public page in a local headless browser and downloads the media addresses that page already carries.
 * It never signs, decrypts or bypasses anything: it reads the same page a user would open.</p>
 */
public interface DouyinVideoClient {
    /**
     * @param cookieFile optional Netscape cookie file exported by the user; never logged
     * @throws DouyinVideoException when no browser is available or the page carries no playable address
     */
    DouyinVideoInfo inspect(ValidatedUrl url, Path cookieFile) throws DouyinVideoException;

    DownloadHandle download(DouyinVideoRequest request, ProgressListener progressListener)
            throws DownloadException;
}
