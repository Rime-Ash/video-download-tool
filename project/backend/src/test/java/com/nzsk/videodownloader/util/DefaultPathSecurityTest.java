package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.StorageException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPathSecurityTest {
    @Test
    void resolvesInsideDirectoryAndCreatesUniqueName() throws Exception {
        var directory = Files.createTempDirectory("video-downloader");
        var existing = directory.resolve("video.mp4");
        Files.createFile(existing);

        var result = new DefaultPathSecurity().resolveOutputPath(directory, "video.mp4");

        assertTrue(result.startsWith(directory.toAbsolutePath().normalize()));
        assertFalse(result.equals(existing));
    }

    @Test
    void sanitizesTraversalInput() throws Exception {
        var directory = Files.createTempDirectory("video-downloader");

        var result = new DefaultPathSecurity().resolveOutputPath(directory, "..\\outside.mp4");

        assertTrue(result.startsWith(directory.toAbsolutePath().normalize()));
    }

    @Test
    void generatesUniqueBaseNameWhenTitleAlreadyExists() throws Exception {
        var directory = Files.createTempDirectory("video-downloader");
        Files.createFile(directory.resolve("视频.mp4"));

        var result = new DefaultPathSecurity().resolveUniqueBaseName(directory, "视频.mp4");

        assertTrue(result.startsWith(directory.toAbsolutePath().normalize()));
        assertEquals("视频 (1)", result.getFileName().toString());
    }

    @Test
    void keepsBaseNameInsideDownloadDirectoryForTraversalInput() throws Exception {
        var directory = Files.createTempDirectory("video-downloader");

        var result = new DefaultPathSecurity().resolveUniqueBaseName(directory, "..\\evil");

        assertTrue(result.startsWith(directory.toAbsolutePath().normalize()));
        assertFalse(result.getFileName().toString().contains("\\"));
    }
}
