package com.nzsk.videodownloader.model;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Optional yt-dlp parameters derived from the user configuration.
 *
 * <p>Every value is validated here so that command construction never has to trust raw input.
 * Proxy, cookie and rate limit values are never logged by the application.</p>
 */
public record DownloadOptions(
        String rateLimit,
        URI proxy,
        Path cookieFile,
        Path ffmpegPath,
        String cookieBrowser
) {
    public static final DownloadOptions NONE = new DownloadOptions(null, null, null, null, null);

    private static final Pattern RATE_LIMIT = Pattern.compile("\\d{1,6}(\\.\\d{1,3})?[KMGkmg]?");
    private static final Pattern COOKIE_BROWSER =
            Pattern.compile("[a-z]{3,16}(:[a-z0-9 ._-]{1,64})?");

    public DownloadOptions {
        rateLimit = rateLimit == null || rateLimit.isBlank() ? null : rateLimit.trim();
        if (rateLimit != null && !RATE_LIMIT.matcher(rateLimit).matches()) {
            throw new IllegalArgumentException("限速格式无效，请使用类似 500K、1M 或 1.5M 的值。");
        }
        if (proxy != null) {
            String scheme = proxy.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("代理地址仅支持 HTTP 或 HTTPS。");
            }
            if (proxy.getHost() == null || proxy.getUserInfo() != null) {
                throw new IllegalArgumentException("代理地址格式无效。");
            }
        }
        cookieFile = cookieFile == null || cookieFile.toString().isBlank() ? null : cookieFile;
        ffmpegPath = ffmpegPath == null || ffmpegPath.toString().isBlank() ? null : ffmpegPath;
        if (cookieBrowser == null || cookieBrowser.isBlank()) {
            cookieBrowser = null;
        } else {
            String trimmed = cookieBrowser.trim();
            String lowerCase = trimmed.toLowerCase(Locale.ROOT);
            if (!COOKIE_BROWSER.matcher(lowerCase).matches()) {
                throw new IllegalArgumentException(
                        "浏览器名称无效，请使用 chrome、edge、firefox 等，或形如 firefox:Profile 1 的写法。");
            }
            // yt-dlp expects a lower case browser name; a profile name keeps its original spelling.
            int separator = trimmed.indexOf(':');
            cookieBrowser = separator < 0
                    ? lowerCase
                    : lowerCase.substring(0, separator) + trimmed.substring(separator);
        }
    }
}
