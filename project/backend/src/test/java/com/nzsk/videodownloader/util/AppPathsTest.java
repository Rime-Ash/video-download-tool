package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {
    @Test
    void findsToolInAncestorToolsDirectory() throws Exception {
        Path root = Files.createTempDirectory("video-downloader-paths");
        Path toolsDirectory = Files.createDirectories(root.resolve("tools"));
        Path expected = Files.createFile(toolsDirectory.resolve("yt-dlp.exe"));
        Path start = Files.createDirectories(root.resolve("app/lib/nested"));

        Path found = AppPaths.findTool(start, "yt-dlp.exe");

        assertEquals(expected.toAbsolutePath().normalize(), found);
    }

    @Test
    void findsToolNextToApplicationDirectory() throws Exception {
        Path root = Files.createTempDirectory("video-downloader-paths");
        Path expected = Files.createFile(root.resolve("ffmpeg.exe"));
        Path start = Files.createDirectories(root.resolve("app"));

        assertEquals(expected.toAbsolutePath().normalize(),
                AppPaths.findTool(start, "ffmpeg.exe"));
    }

    @Test
    void returnsNullWhenToolIsMissing() throws Exception {
        Path root = Files.createTempDirectory("video-downloader-paths");

        assertNull(AppPaths.findTool(root, "yt-dlp.exe"));
    }

    @Test
    void defaultConfigUsesWhitelistedFriendlyFormatSelector() {
        var config = AppPaths.defaultConfig();

        assertTrue(config.defaultFormatSelector().contains("bestvideo"));
        assertTrue(config.maxConcurrentDownloads() >= 1);
    }

    @Test
    void keepsConfiguredToolWhileTheFileExists() throws Exception {
        Path configured = Files.createFile(
                Files.createTempDirectory("video-downloader-paths").resolve("yt-dlp.exe"));
        Path discovered = Path.of("tools", "yt-dlp.exe");

        assertEquals(configured, AppPaths.preferredToolPath(configured, discovered));
    }

    @Test
    void fallsBackToTheDiscoveredToolWhenTheConfiguredPathIsStale() {
        Path discovered = Path.of("tools", "yt-dlp.exe");

        assertEquals(discovered,
                AppPaths.preferredToolPath(Path.of("removed-app", "tools", "yt-dlp.exe"), discovered));
        assertEquals(discovered, AppPaths.preferredToolPath(null, discovered));
    }
}
