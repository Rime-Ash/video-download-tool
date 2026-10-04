package com.nzsk.videodownloader.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of inspecting a link: either a video or an image post.
 */
public record PostInspection(VideoInfo video, ImagePostInfo imagePost) {
    public static PostInspection ofVideo(VideoInfo video) {
        return new PostInspection(Objects.requireNonNull(video, "video"), null);
    }

    public static PostInspection ofImagePost(ImagePostInfo imagePost) {
        return new PostInspection(null, Objects.requireNonNull(imagePost, "imagePost"));
    }

    public boolean isVideo() {
        return video != null;
    }

    public Optional<VideoInfo> videoInfo() {
        return Optional.ofNullable(video);
    }

    public Optional<ImagePostInfo> imagePostInfo() {
        return Optional.ofNullable(imagePost);
    }
}
