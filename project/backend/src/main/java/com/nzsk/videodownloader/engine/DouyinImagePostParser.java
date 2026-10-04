package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.model.ImageInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.LivePhotoInfo;
import com.nzsk.videodownloader.util.ImageCdnHosts;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns the Douyin web detail JSON of an image post into an {@link ImagePostInfo}.
 *
 * <p>Only the images of the inspected post are read. Image URLs are validated against the media CDN
 * allowlist, so a crafted response cannot redirect the download to another host.</p>
 */
public final class DouyinImagePostParser {
    private static final int AWEME_TYPE_IMAGES = 68;

    private DouyinImagePostParser() {
    }

    public static Optional<ImagePostInfo> parse(String json, ObjectMapper objectMapper)
            throws ImagePostException {
        return parse(json, objectMapper, ImageCdnHosts::isAllowed);
    }

    /**
     * @param imageHostPolicy which image hosts may be kept; production passes the media CDN allowlist
     */
    public static Optional<ImagePostInfo> parse(String json, ObjectMapper objectMapper,
                                                java.util.function.Predicate<URI> imageHostPolicy)
            throws ImagePostException {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(imageHostPolicy, "imageHostPolicy");
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (IOException exception) {
            throw new ImagePostException("图集接口返回的内容无法解析，可能需要更新程序或稍后重试。", exception);
        }
        JsonNode detail = root.path("aweme_detail");
        if (detail.isMissingNode() || detail.isNull()) {
            return Optional.empty();
        }
        int awemeType = detail.path("aweme_type").asInt(0);
        JsonNode images = detail.path("images");
        if (awemeType != AWEME_TYPE_IMAGES || !images.isArray() || images.isEmpty()) {
            return Optional.empty();
        }

        List<ImageInfo> collected = new ArrayList<>();
        for (JsonNode image : images) {
            selectUrl(image, imageHostPolicy).ifPresent(url -> collected.add(new ImageInfo(
                    url,
                    extensionOf(url),
                    integer(image, "width"),
                    integer(image, "height"),
                    parseLivePhoto(image, imageHostPolicy))));
        }
        if (collected.isEmpty()) {
            throw new ImagePostException("该图集没有可用的图片地址，可能需要重新导出 Cookie 后重试。");
        }
        return Optional.of(new ImagePostInfo(
                detail.path("aweme_id").asText(""),
                text(detail, "desc"),
                detail.path("author").path("nickname").asText(null),
                collected));
    }

    /**
     * Live photos (动图/实况) carry a short video in {@code images[].video}. It is only kept when a playable
     * address on the media CDN exists, so the download stays inside the allowlist.
     */
    private static LivePhotoInfo parseLivePhoto(JsonNode image,
                                                java.util.function.Predicate<URI> imageHostPolicy) {
        JsonNode video = image.path("video");
        if (video.isMissingNode() || video.isNull()) {
            return null;
        }
        long duration = video.path("duration").asLong(0);
        if (duration <= 0) {
            return null;
        }
        String url = firstAllowedUrl(video.path("play_addr_h264"), imageHostPolicy)
                .or(() -> firstAllowedUrl(video.path("play_addr"), imageHostPolicy))
                .orElse(null);
        if (url == null) {
            return null;
        }
        return new LivePhotoInfo(url, duration, integer(video, "width"), integer(video, "height"));
    }

    private static Optional<String> firstAllowedUrl(JsonNode addressNode,
                                                    java.util.function.Predicate<URI> imageHostPolicy) {
        JsonNode urlList = addressNode.path("url_list");
        if (!urlList.isArray()) {
            return Optional.empty();
        }
        for (JsonNode node : urlList) {
            String url = node.asText(null);
            if (url == null || url.isBlank()) {
                continue;
            }
            try {
                if (imageHostPolicy.test(URI.create(url))) {
                    return Optional.of(url);
                }
            } catch (IllegalArgumentException ignored) {
                // invalid URL in the response: skip it
            }
        }
        return Optional.empty();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    /** Prefers a non-webp URL because it opens in every Windows viewer. */
    private static Optional<String> selectUrl(JsonNode image,
                                              java.util.function.Predicate<URI> imageHostPolicy) {
        JsonNode urlList = image.path("url_list");
        if (!urlList.isArray() || urlList.isEmpty()) {
            return Optional.empty();
        }
        String fallback = null;
        for (JsonNode node : urlList) {
            String url = node.asText(null);
            if (url == null || url.isBlank()) {
                continue;
            }
            if (!imageHostPolicy.test(URI.create(url))) {
                continue;
            }
            if (fallback == null) {
                fallback = url;
            }
            String path = URI.create(url).getPath() == null
                    ? "" : URI.create(url).getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".png")) {
                return Optional.of(url);
            }
        }
        return Optional.ofNullable(fallback);
    }

    private static String extensionOf(String url) {
        String path = URI.create(url).getPath();
        if (path == null) {
            return "jpg";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String candidate : List.of("jpeg", "jpg", "png", "webp", "heic")) {
            if (lower.endsWith("." + candidate)) {
                return candidate.equals("jpeg") ? "jpg" : candidate;
            }
        }
        return "jpg";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
