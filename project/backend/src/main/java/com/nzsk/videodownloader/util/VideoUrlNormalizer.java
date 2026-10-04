package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.UrlValidationException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Rejects link forms that are not a single video and rewrites share links that carry the video id in a
 * query parameter.
 *
 * <p>Only strictly numeric video ids are accepted for a rewrite, so a crafted query string can never inject
 * another path. Account pages, playlists and collection pages are refused instead of being handed to
 * yt-dlp, which keeps the application inside its single-video scope.</p>
 */
public final class VideoUrlNormalizer {
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{6,}");

    private VideoUrlNormalizer() {
    }

    public static URI normalize(URI uri) throws UrlValidationException {
        String host = uri.getHost();
        if (host == null) {
            return uri;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (normalizedHost.endsWith("douyin.com") && path.startsWith("/user/")) {
            String videoId = firstNumericParameter(uri.getRawQuery(), "modal_id", "vid");
            if (videoId == null) {
                throw new UrlValidationException("该链接是抖音账号主页，本软件只支持单个视频链接。"
                        + "请在视频播放页使用“分享 → 复制链接”，再粘贴得到的链接。");
            }
            return URI.create("https://www.douyin.com/video/" + videoId);
        }

        boolean bilibiliHost = normalizedHost.endsWith("bilibili.com");
        boolean accountPage = "space.bilibili.com".equals(normalizedHost)
                || path.startsWith("/space/")
                || path.startsWith("/medialist/")
                || path.startsWith("/account/");
        if (bilibiliHost && accountPage) {
            throw new UrlValidationException("该链接是账号主页或合集列表，本软件只支持单个视频链接。"
                    + "请打开具体视频页面后复制它的链接。");
        }

        return uri;
    }

    private static String firstNumericParameter(String rawQuery, String... names) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            for (String candidate : names) {
                if (name.equalsIgnoreCase(candidate) && NUMERIC_ID.matcher(value).matches()) {
                    return value;
                }
            }
        }
        return null;
    }
}
