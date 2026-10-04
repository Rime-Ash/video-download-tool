package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadedFileLocatorTest {
    @Test
    void findsFileWithAnyExtensionAndIgnoresPartFiles() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-locate");
        Files.createFile(directory.resolve("标题.mp4.part"));
        var expected = Files.createFile(directory.resolve("标题.mkv"));

        var found = DownloadedFileLocator.find(directory, "标题.mp4");

        assertTrue(found.isPresent());
        assertEquals(expected.toAbsolutePath().normalize(), found.orElseThrow());
    }

    @Test
    void returnsEmptyWhenNothingMatches() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-locate");
        Files.createFile(directory.resolve("other.mp4"));

        assertTrue(DownloadedFileLocator.find(directory, "标题").isEmpty());
    }
}
