package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.DownloadState;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ProgressParser {
    /**
     * Literal marker embedded in the yt-dlp progress template.
     *
     * <p>yt-dlp treats the text before the first colon of {@code --progress-template} as the template type
     * selector and strips it from the output, so the recognizable prefix has to live inside the template
     * itself. {@code CommandBuilder} and this parser share the constant to stay in sync.</p>
     */
    public static final String PROGRESS_MARKER = "vd-progress:";
    private static final List<String> UNKNOWN_SUFFIXES = List.of("/n/a", "/na");

    private ProgressParser() {
    }

    public static Optional<DownloadProgress> parse(String line) {
        if (line == null || !line.startsWith(PROGRESS_MARKER)) {
            return Optional.empty();
        }
        String[] fields = line.substring(PROGRESS_MARKER.length()).split("\\|", -1);
        if (fields.length != 4) {
            return Optional.empty();
        }
        try {
            double percentage = Double.parseDouble(fields[0].replace("%", "").trim());
            return Optional.of(new DownloadProgress(
                    Math.max(0, Math.min(100, percentage)),
                    normalize(fields[1]),
                    normalize(fields[2]),
                    normalize(fields[3]),
                    DownloadState.DOWNLOADING
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /**
     * yt-dlp prints {@code NA} or {@code N/A} while a value is still unknown; the interface shows nothing
     * instead. A size pair such as {@code 1.00KiB/N/A} keeps the part that is already known.
     */
    private static String normalize(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (isUnknown(trimmed)) {
            return "";
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        for (String suffix : UNKNOWN_SUFFIXES) {
            if (lowered.endsWith(suffix) && lowered.length() > suffix.length()) {
                return collapseSpaces(trimmed.substring(0, trimmed.length() - suffix.length()));
            }
        }
        return collapseSpaces(trimmed);
    }

    private static boolean isUnknown(String value) {
        return value.isEmpty()
                || "NA".equalsIgnoreCase(value)
                || "N/A".equalsIgnoreCase(value);
    }

    /** yt-dlp pads its formatted values for the terminal; the queue column does not need that padding. */
    private static String collapseSpaces(String value) {
        return value.replaceAll("\\s+", " ")
                .replaceAll("\\s*/\\s*", "/")
                .trim();
    }
}
