package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.model.DownloadOptions;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBuilderTest {
    private static final ValidatedUrl URL =
            new ValidatedUrl(URI.create("https://www.bilibili.com/video/BV1"), "www.bilibili.com");

    @Test
    void buildsArgumentListWithoutShellWrapper() {
        List<String> command = CommandBuilder.downloadCommand(
                Path.of("tools/yt-dlp.exe"), request("bestvideo+bestaudio/best", DownloadOptions.NONE));

        assertTrue(command.contains("--continue"));
        assertTrue(command.contains("--windows-filenames"));
        assertTrue(command.contains("--no-overwrites"));
        assertFalse(command.contains("cmd"));
        assertFalse(command.contains("/c"));
        assertFalse(command.stream().anyMatch(value -> value.contains("powershell")));
    }

    @Test
    void keepsOutputFileInsideDownloadDirectory() {
        List<String> command = CommandBuilder.downloadCommand(
                Path.of("tools/yt-dlp.exe"), request("best", DownloadOptions.NONE));

        String output = command.get(command.indexOf("-o") + 1);
        Path downloadDirectory = Path.of("C:/Downloads").toAbsolutePath().normalize();

        assertTrue(output.startsWith(downloadDirectory.toString()));
        assertTrue(output.endsWith(".%(ext)s"));
        assertFalse(output.contains(".."));
    }

    @Test
    void sanitizesUnsafeCharactersInBaseFileName() {
        DownloadRequest request = new DownloadRequest(
                URL, "best", Path.of("C:/Downloads"), "a:b*c?.mp4", false, DownloadOptions.NONE);

        List<String> command = CommandBuilder.downloadCommand(Path.of("tools/yt-dlp.exe"), request);
        String output = command.get(command.indexOf("-o") + 1);
        String fileName = Path.of(output).getFileName().toString();

        assertFalse(fileName.contains(":"));
        assertFalse(fileName.contains("*"));
        assertFalse(fileName.contains("?"));
    }

    @Test
    void rejectsBaseFileNameWithPathSeparators() {
        assertThrows(IllegalArgumentException.class, () -> new DownloadRequest(
                URL, "best", Path.of("C:/Downloads"), "../outside.mp4", false, DownloadOptions.NONE));
    }

    @Test
    void addsConfiguredToolAndNetworkOptions() {
        Path cookieFile = Path.of("C:/profile/cookies.txt");
        Path ffmpeg = Path.of("C:/tools/ffmpeg.exe");
        DownloadOptions options = new DownloadOptions(
                "2M", URI.create("http://127.0.0.1:7890"), cookieFile, ffmpeg, null);

        List<String> command = CommandBuilder.downloadCommand(
                Path.of("tools/yt-dlp.exe"), request("best", options));

        assertEquals("2M", command.get(command.indexOf("--limit-rate") + 1));
        assertEquals("http://127.0.0.1:7890", command.get(command.indexOf("--proxy") + 1));
        assertEquals(cookieFile.toString(), command.get(command.indexOf("--cookies") + 1));
        assertEquals(ffmpeg.toString(), command.get(command.indexOf("--ffmpeg-location") + 1));
    }

    @Test
    void rejectsUnsafeRateLimitAndProxyValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadOptions("2M; del *", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadOptions(null, URI.create("file:///C:/temp"), null, null, null));
    }
    @Test
    void prefersExplicitCookieFileOverBrowserProfile() {
        DownloadOptions options = new DownloadOptions(
                null, null, Path.of("C:/profile/cookies.txt"), null, "edge");

        List<String> command = CommandBuilder.downloadCommand(
                Path.of("tools/yt-dlp.exe"), request("best", options));

        assertEquals(Path.of("C:/profile/cookies.txt").toString(),
                command.get(command.indexOf("--cookies") + 1));
        assertFalse(command.contains("--cookies-from-browser"));
    }

    @Test
    void usesCookieFileWhenNoBrowserIsConfigured() {
        DownloadOptions options = new DownloadOptions(
                null, null, Path.of("C:/profile/cookies.txt"), null, null);

        List<String> command = CommandBuilder.downloadCommand(
                Path.of("tools/yt-dlp.exe"), request("best", options));

        assertTrue(command.contains("--cookies"));
        assertFalse(command.contains("--cookies-from-browser"));
    }

    @Test
    void inspectionCommandUsesArgumentListAndConfiguredCookies() {
        Path cookieFile = Path.of("C:/profile/cookies.txt");
        List<String> command = CommandBuilder.inspectionCommand(
                Path.of("tools/yt-dlp.exe"),
                "https://www.bilibili.com/video/BV1",
                new DownloadOptions(null, null, cookieFile, null, null));

        assertTrue(command.contains("-J"));
        assertTrue(command.contains("--no-playlist"));
        assertEquals(cookieFile.toString(), command.get(command.indexOf("--cookies") + 1));
    }

    @Test
    void rejectsNullInspectionUrl() {
        assertThrows(NullPointerException.class,
                () -> CommandBuilder.inspectionCommand(Path.of("tools/yt-dlp.exe"), null));
    }

    private static DownloadRequest request(String selector, DownloadOptions options) {
        return new DownloadRequest(
                URL, selector, Path.of("C:/Downloads"), "视频标题", true, options);
    }
}
