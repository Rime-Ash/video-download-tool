package com.nzsk.videodownloader.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Locates the media file that was written for a unique base name.
 *
 * <p>The download command uses an explicit output template, so the produced file is
 * {@code <base>.<extension>}. The extension is chosen by yt-dlp (it can change when streams are merged),
 * therefore the finished file is resolved by scanning the download directory instead of guessing.</p>
 */
public final class DownloadedFileLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadedFileLocator.class);
    private static final int MAX_BASE_NAME_LENGTH = 120;

    private DownloadedFileLocator() {
    }

    public static Optional<Path> find(Path downloadDirectory, String baseFileName) {
        if (downloadDirectory == null || baseFileName == null || baseFileName.isBlank()) {
            return Optional.empty();
        }
        Path directory = downloadDirectory.toAbsolutePath().normalize();
        String expected = FileNameSanitizer.sanitize(
                        FileNameSanitizer.stripMediaExtension(baseFileName), MAX_BASE_NAME_LENGTH)
                .toLowerCase(Locale.ROOT);
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> !isTemporaryFile(path))
                    .filter(path -> baseNameOf(path).equals(expected))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
        } catch (IOException exception) {
            LOGGER.warn("无法扫描下载目录以确认输出文件。");
            return Optional.empty();
        }
    }

    private static boolean isTemporaryFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".part") || name.endsWith(".ytdl") || name.endsWith(".temp");
    }

    private static String baseNameOf(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
