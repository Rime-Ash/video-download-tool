package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserLocatorTest {
    @Test
    void returnsFirstExistingCandidate() throws Exception {
        Path directory = Files.createTempDirectory("browser-locator");
        Path existing = Files.createFile(directory.resolve("msedge.exe"));
        Path missing = directory.resolve("chrome.exe");

        assertEquals(existing, BrowserLocator.locate(List.of(existing, missing)).orElseThrow());
    }

    @Test
    void ignoresCandidatesThatAreNotFiles() throws Exception {
        Path directory = Files.createTempDirectory("browser-locator-none");

        assertTrue(BrowserLocator.locate(List.of(directory.resolve("chrome.exe"))).isEmpty());
        assertTrue(BrowserLocator.locate(null).isEmpty());
    }
}
