package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.AppConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentCheckServiceTest {
    @Test
    void reportsJavaRequirementAndWritableDirectory() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-env");
        var config = new AppConfig(directory, 1, null, null, null, null,
                "bestvideo+bestaudio/best", directory.resolve("yt-dlp.exe"), directory.resolve("ffmpeg.exe"));

        var status = new EnvironmentCheckService(1).check(config);

        assertTrue(status.javaRuntimeAvailable());
        assertTrue(status.downloadDirectoryWritable());
        assertFalse(status.ytDlpAvailable());
        assertFalse(status.ffmpegAvailable());
    }
}
