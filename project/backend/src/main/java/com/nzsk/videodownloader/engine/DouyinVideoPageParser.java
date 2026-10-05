package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.model.DouyinVideoFormat;
import com.nzsk.videodownloader.model.DouyinVideoInfo;
import com.nzsk.videodownloader.util.MediaCdnHosts;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads title, author, cover and the playable media addresses out of a rendered Douyin video page.
 *
 * <p>Douyin answers plain API requests with HTTP 403 unless they carry a signature that only its own
 * JavaScript produces, but the rendered page is served to any browser. The player element of that page
 * already contains signed, directly downloadable media URLs, so the fallback reads them from the markup
 * instead of calling the API.</p>
 *
 * <p>Only URLs on the media CDN allowlist are kept, so a manipulated page cannot redirect a download to an
 * arbitrary host.</p>
 */
public final class DouyinVideoPageParser {
    private static final int MAX_FORMATS = 8;
    private static final Pattern META_TAG =
            Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDIA_TAG =
            Pattern.compile("<(?:source|video)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_ATTRIBUTE =
            Pattern.compile("\\bname\\s*=\\s*\"([^\"]*)\"|\\bname\\s*=\\s*'([^']*)'",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTRIBUTE =
            Pattern.compile("\\bcontent\\s*=\\s*\"([^\"]*)\"|\\bcontent\\s*=\\s*'([^']*)'",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SRC_ATTRIBUTE =
            Pattern.compile("\\bsrc\\s*=\\s*\"([^\"]*)\"|\\bsrc\\s*=\\s*'([^']*)'",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_ELEMENT =
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern AUTHOR_IN_DESCRIPTION =
            Pattern.compile("-\\s*(.{1,40}?)\\s*于\\s*\\d{8}\\s*发布在抖音");
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            "js", "css", "json", "ico", "png", "jpg", "jpeg", "webp", "gif", "svg", "woff", "woff2",
            "ttf", "map", "html");
    private static final Set<String> DIRECT_CDN_SUFFIXES = Set.of("douyinvod.com");
    private static final String TITLE_META = "lark:url:video_title";
    private static final String COVER_META = "lark:url:video_cover_image_url";
    private static final String PAGE_TITLE_SUFFIX = " - 抖音";

    private DouyinVideoPageParser() {
    }

    public static Optional<DouyinVideoInfo> parse(String html, String postId) {
        return parse(html, postId, MediaCdnHosts::isAllowed);
    }

    /**
     * @param hostPolicy which media hosts may be kept; production passes the media CDN allowlist
     * @return the resolved video, or empty when the page contains no playable address
     */
    public static Optional<DouyinVideoInfo> parse(String html, String postId,
                                                  Predicate<URI> hostPolicy) {
        if (html == null || html.isBlank() || postId == null || postId.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> meta = metaTags(html);
        List<DouyinVideoFormat> formats = formats(html, hostPolicy);
        if (formats.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new DouyinVideoInfo(
                postId,
                firstNonBlank(withoutPageSuffix(meta.get(TITLE_META)),
                        withoutPageSuffix(titleElement(html))),
                author(meta.get("description")),
                null,
                meta.get(COVER_META),
                formats));
    }

    private static Map<String, String> metaTags(String html) {
        Map<String, String> meta = new LinkedHashMap<>();
        Matcher matcher = META_TAG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            String name = attribute(tag, NAME_ATTRIBUTE);
            String content = attribute(tag, CONTENT_ATTRIBUTE);
            if (name != null && content != null) {
                meta.putIfAbsent(name.toLowerCase(Locale.ROOT), unescape(content));
            }
        }
        return meta;
    }

    private static List<DouyinVideoFormat> formats(String html, Predicate<URI> hostPolicy) {
        List<String> direct = new ArrayList<>();
        List<String> other = new ArrayList<>();
        Matcher matcher = MEDIA_TAG.matcher(html);
        while (matcher.find()) {
            String raw = attribute(matcher.group(), SRC_ATTRIBUTE);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String candidate = unescape(raw);
            Optional<URI> uri = parseUri(candidate);
            if (uri.isEmpty() || !hostPolicy.test(uri.get()) || mediaExtension(uri.get()).isEmpty()) {
                continue;
            }
            List<String> bucket = isDirectCdn(uri.get()) ? direct : other;
            if (!direct.contains(candidate) && !other.contains(candidate)) {
                bucket.add(candidate);
            }
        }
        List<String> ordered = new ArrayList<>(direct);
        ordered.addAll(other);
        List<DouyinVideoFormat> formats = new ArrayList<>();
        for (String url : ordered) {
            if (formats.size() >= MAX_FORMATS) {
                break;
            }
            URI uri = parseUri(url).orElseThrow();
            String extension = mediaExtension(uri).orElse("mp4");
            formats.add(new DouyinVideoFormat(
                    "源 " + (formats.size() + 1),
                    url,
                    extension,
                    isDirectCdn(uri) ? "浏览器解析（CDN 直链）" : "浏览器解析"));
        }
        return List.copyOf(formats);
    }

    private static Optional<URI> parseUri(String value) {
        try {
            return Optional.of(URI.create(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * @return the file extension, {@code mp4} for extension-less CDN paths, or empty when the address is a
     *         static asset such as a cover image or a script
     */
    private static Optional<String> mediaExtension(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return Optional.of("mp4");
        }
        String extension = path.substring(dot + 1);
        if (STATIC_EXTENSIONS.contains(extension) || extension.length() > 5 || extension.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(extension);
    }

    private static boolean isDirectCdn(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return DIRECT_CDN_SUFFIXES.stream()
                .anyMatch(suffix -> normalized.equals(suffix) || normalized.endsWith("." + suffix));
    }

    private static String author(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        Matcher matcher = AUTHOR_IN_DESCRIPTION.matcher(description);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    private static String titleElement(String html) {
        Matcher matcher = TITLE_ELEMENT.matcher(html);
        return matcher.find() ? unescape(matcher.group(1)).strip() : null;
    }

    private static String withoutPageSuffix(String title) {
        if (title == null) {
            return null;
        }
        String trimmed = title.strip();
        return trimmed.endsWith(PAGE_TITLE_SUFFIX)
                ? trimmed.substring(0, trimmed.length() - PAGE_TITLE_SUFFIX.length()).strip()
                : trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String attribute(String tag, Pattern pattern) {
        Matcher matcher = pattern.matcher(tag);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
    }

    /** The player markup escapes query separators as {@code &amp;}, so they have to be restored. */
    private static String unescape(String value) {
        String result = value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        result = result.replaceAll("&#(\\d{1,7});", "");
        return result.replace("&amp;", "&");
    }
}
