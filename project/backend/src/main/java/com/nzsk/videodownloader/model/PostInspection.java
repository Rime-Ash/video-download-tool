package com.nzsk.videodownloader.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of inspecting a link: a video inspected by yt-dlp, a Douyin video resolved by the browser
 * fallback, or an image post.
 */
public record PostInspection(VideoInfo video, ImagePostInfo imagePost, DouyinVideoInfo douyinVideo) {
    public static PostInspection ofVideo(VideoInfo video) {
        return new PostInspection(Objects.requireNonNull(video, "video"), null, null);
    }

    public static PostInspection ofImagePost(ImagePostInfo imagePost) {
        return new PostInspection(null, Objects.requireNonNull(imagePost, "imagePost"), null);
    }

    public static PostInspection ofDouyinVideo(DouyinVideoInfo douyinVideo) {
        return new PostInspection(null, null, Objects.requireNonNull(douyinVideo, "douyinVideo"));
    }

    public boolean isVideo() {
        return video != null || douyinVideo != null;
    }

    public Optional<VideoInfo> videoInfo() {
        return Optional.ofNullable(video);
    }

    public Optional<ImagePostInfo> imagePostInfo() {
        return Optional.ofNullable(imagePost);
    }

    public Optional<DouyinVideoInfo> douyinVideoInfo() {
        return Optional.ofNullable(douyinVideo);
    }
}
