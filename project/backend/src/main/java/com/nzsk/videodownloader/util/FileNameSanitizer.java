package com.nzsk.videodownloader.util;

import java.util.List;
import java.util.Locale;

public final class FileNameSanitizer {
    private static final int DEFAULT_MAX_LENGTH = 180;
    private static final String INVALID_CHARS = "[\\\\/:*?\"<>|]";
    private static final List<String> MEDIA_EXTENSIONS = List.of(
            ".mp4", ".mkv", ".webm", ".flv", ".mov", ".avi", ".m4a", ".mp3", ".aac", ".wav");

    private FileNameSanitizer() {
    }

    public static String sanitize(String rawName) {
        return sanitize(rawName, DEFAULT_MAX_LENGTH);
    }

    public static String sanitize(String rawName, int maxLength) {
        if (rawName == null || rawName.isBlank()) {
            return "download";
        }
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength must be positive");
        }
        String sanitized = rawName.replaceAll(INVALID_CHARS, "_")
                .replaceAll("[\\p{Cntrl}]", "_")
                .strip();
        sanitized = sanitized.replaceAll("[ .]+$", "");
        if (sanitized.isBlank()) {
            sanitized = "download";
        }
        if (isReservedDeviceName(sanitized)) {
            sanitized = "_" + sanitized;
        }
        String truncated = truncate(sanitized, maxLength).strip();
        return truncated.isBlank() ? "download" : truncated;
    }

    /**
     * Truncates without splitting a surrogate pair. A lone half cannot be encoded in a Windows file name, so
     * a title that ends in an emoji at exactly the length limit would otherwise fail the whole download.
     */
    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    /** Removes a trailing media extension so a title can be used as a base name. */
    public static String stripMediaExtension(String rawName) {
        if (rawName == null) {
            return null;
        }
        String lowerCase = rawName.toLowerCase(Locale.ROOT);
        for (String extension : MEDIA_EXTENSIONS) {
            if (lowerCase.endsWith(extension)) {
                return rawName.substring(0, rawName.length() - extension.length());
            }
        }
        return rawName;
    }

    private static boolean isReservedDeviceName(String value) {
        String base = value.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        return switch (base) {
            case "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" -> true;
            default -> false;
        };
    }
}
