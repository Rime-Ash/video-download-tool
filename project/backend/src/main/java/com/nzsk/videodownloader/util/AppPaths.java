package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.model.AppConfig;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves application directories without hard-coding machine specific paths.
 *
 * <p>Bundled tools are looked up next to the running application first and in the working
 * directory as a fallback so that both packaged and development runs work.</p>
 */
public final class AppPaths {
    public static final String DEFAULT_FORMAT_SELECTOR = "bestvideo+bestaudio/best";
    public static final String YT_DLP_EXE = "yt-dlp.exe";
    public static final String FFMPEG_EXE = "ffmpeg.exe";

    private static final String APP_VENDOR = "NZSK";
    private static final String APP_NAME = "VideoDownloader";
    private static final int MAX_TOOL_SEARCH_LEVELS = 8;

    private AppPaths() {
    }

    public static Path applicationDirectory() {
        try {
            Path location = Path.of(AppPaths.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (Exception exception) {
            return Path.of(System.getProperty("user.dir"));
        }
    }

    /**
     * Looks for a bundled tool next to the running code and in every ancestor directory, so that both
     * a packaged image ({@code app/tools}, {@code <app>/tools}) and a development build find it.
     *
     * @return the resolved path, or {@code null} when no regular file was found
     */
    public static Path findTool(Path startDirectory, String fileName) {
        Path current = startDirectory == null ? null : startDirectory.toAbsolutePath().normalize();
        for (int level = 0; level < MAX_TOOL_SEARCH_LEVELS && current != null; level++) {
            Path inToolsDirectory = current.resolve("tools").resolve(fileName);
            if (Files.isRegularFile(inToolsDirectory)) {
                return inToolsDirectory;
            }
            Path nextToApplication = current.resolve(fileName);
            if (Files.isRegularFile(nextToApplication)) {
                return nextToApplication;
            }
            current = current.getParent();
        }
        return null;
    }

    public static Path bundledTool(String fileName) {
        Path found = findTool(applicationDirectory(), fileName);
        if (found != null) {
            return found;
        }
        return Path.of(System.getProperty("user.dir"), "tools", fileName)
                .toAbsolutePath().normalize();
    }

    /**
     * Keeps a tool path from the configuration while that file still exists and otherwise falls back to the
     * tool discovered next to the running application.
     *
     * <p>The user configuration outlives an installation, so an absolute path written by an earlier copy of
     * the application would otherwise keep pointing at a directory that no longer exists.</p>
     */
    public static Path preferredToolPath(Path configured, Path discovered) {
        return configured != null && Files.isRegularFile(configured) ? configured : discovered;
    }

    public static Path ytDlpPath() {
        return bundledTool(YT_DLP_EXE);
    }

    public static Path ffmpegPath() {
        return bundledTool(FFMPEG_EXE);
    }

    public static Path configFile() {
        return roamingAppDataDirectory().resolve(APP_VENDOR).resolve(APP_NAME).resolve("config.json");
    }

    public static Path configDirectory() {
        return configFile().getParent();
    }

    public static Path logDirectory() {
        return localAppDataDirectory().resolve(APP_VENDOR).resolve(APP_NAME).resolve("logs");
    }

    /** {@code %LOCALAPPDATA%}, used by browsers and by this application for logs. */
    public static Path localAppDataDirectory() {
        return environmentPath("LOCALAPPDATA")
                .orElseGet(() -> Path.of(System.getProperty("user.home"), "AppData", "Local"));
    }

    /** {@code %APPDATA%}, used by browsers and by this application for configuration. */
    public static Path roamingAppDataDirectory() {
        return environmentPath("APPDATA")
                .orElseGet(() -> Path.of(System.getProperty("user.home"), "AppData", "Roaming"));
    }

    public static Path defaultDownloadDirectory() {
        return Path.of(System.getProperty("user.home"), "Downloads", APP_NAME);
    }

    public static AppConfig defaultConfig() {
        return new AppConfig(
                defaultDownloadDirectory(),
                1,
                null,
                null,
                null,
                null,
                DEFAULT_FORMAT_SELECTOR,
                ytDlpPath(),
                ffmpegPath()
        );
    }

    /** Proxy values configured by the user, or {@code null} when the text is empty. */
    public static URI parseProxy(String rawProxy) {
        if (rawProxy == null || rawProxy.isBlank()) {
            return null;
        }
        return URI.create(rawProxy.trim());
    }

    private static java.util.Optional<Path> environmentPath(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(Path.of(value));
    }
}
