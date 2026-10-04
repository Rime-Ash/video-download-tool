package com.nzsk.videodownloader.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Image URLs returned by the platform API are only fetched when they point at a known media CDN, so a
 * crafted response can never make the application download from an arbitrary host.
 */
public final class ImageCdnHosts {
    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            "douyinpic.com",
            "douyinvod.com",
            "douyinstatic.com",
            "byteimg.com",
            "bytecdn.cn",
            "ixigua.com",
            "snssdk.com",
            "amemv.com",
            "365yg.com",
            "toutiaoimg.com",
            "douyin.com"
    );

    private ImageCdnHosts() {
    }

    public static boolean isAllowed(URI uri) {
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return ALLOWED_SUFFIXES.stream()
                .anyMatch(suffix -> normalized.equals(suffix) || normalized.endsWith("." + suffix));
    }
}
