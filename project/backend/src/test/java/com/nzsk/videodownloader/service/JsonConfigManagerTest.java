package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.AppConfig;
import com.nzsk.videodownloader.util.AppPaths;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonConfigManagerTest {
    @Test
    void savesAndLoadsConfiguration() throws Exception {
        Path directory = Files.createTempDirectory("video-downloader-config");
        Path configFile = directory.resolve("nested/config.json");
        AppConfig expected = new AppConfig(
                directory.resolve("downloads"),
                1,
                "2M",
                URI.create("http://127.0.0.1:8080"),
                directory.resolve("cookies.txt"),
                "edge",
                "bestvideo+bestaudio/best",
                directory.resolve("yt-dlp.exe"),
                directory.resolve("ffmpeg.exe")
        );

        JsonConfigManager manager = new JsonConfigManager(configFile, expected);
        manager.save(expected);

        assertEquals(expected, manager.load());
    }

    @Test
    void loadsConfigurationWithoutBrowserCookieSetting() throws Exception {
        Path directory = Files.createTempDirectory("video-downloader-config");
        Path configFile = directory.resolve("config.json");
        // Forward slashes keep the JSON readable and avoid backslash escaping on Windows.
        String downloads = directory.resolve("downloads").toString().replace('\\', '/');
        String ytDlp = directory.resolve("yt-dlp.exe").toString().replace('\\', '/');
        String ffmpeg = directory.resolve("ffmpeg.exe").toString().replace('\\', '/');
        Files.writeString(configFile, """
                {
                  "downloadDirectory" : "%s",
                  "maxConcurrentDownloads" : 2,
                  "rateLimit" : null,
                  "proxy" : null,
                  "cookieFile" : null,
                  "defaultFormatSelector" : "bestvideo+bestaudio/best",
                  "ytDlpPath" : "%s",
                  "ffmpegPath" : "%s"
                }
                """.formatted(downloads, ytDlp, ffmpeg));

        AppConfig loaded = new JsonConfigManager(configFile, AppPaths.defaultConfig()).load();

        assertEquals(2, loaded.maxConcurrentDownloads());
        assertNull(loaded.cookieBrowser());
    }
}
