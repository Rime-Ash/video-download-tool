package com.nzsk.videodownloader.engine;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the numeric aweme id from a Douyin URL.
 *
 * <p>Supported shapes: {@code /video/<id>}, {@code /note/<id>}, {@code /share/video/<id>},
 * {@code /share/note/<id>} and profile links that carry {@code modal_id} or {@code vid}.</p>
 */
public final class DouyinPostIds {
    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{6,}");

    private DouyinPostIds() {
    }

    public static Optional<String> awemeId(URI uri) {
        if (uri == null) {
            return Optional.empty();
        }
        Matcher pathMatcher = Pattern.compile("/(?:video|note)/(\\d{6,})").matcher(
                uri.getPath() == null ? "" : uri.getPath());
        if (pathMatcher.find()) {
            return Optional.of(pathMatcher.group(1));
        }
        String query = uri.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int separator = pair.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String name = pair.substring(0, separator);
                String value = pair.substring(separator + 1);
                if ((name.equalsIgnoreCase("modal_id") || name.equalsIgnoreCase("vid")
                        || name.equalsIgnoreCase("aweme_id")) && NUMERIC_ID.matcher(value).matches()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    public static boolean isImagePostPath(URI uri) {
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
        return path.contains("/note/");
    }
}
