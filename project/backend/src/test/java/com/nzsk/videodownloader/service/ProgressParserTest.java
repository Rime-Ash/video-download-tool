package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.DownloadState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressParserTest {
    @Test
    void parsesStructuredProgressLine() {
        var progress = ProgressParser.parse("download:42.5%|1024|1.2MiB/s|00:10");

        assertTrue(progress.isPresent());
        assertEquals(42.5, progress.orElseThrow().percentage());
        assertEquals(DownloadState.DOWNLOADING, progress.orElseThrow().state());
    }

    @Test
    void ignoresNonProgressOutput() {
        assertTrue(ProgressParser.parse("WARNING: unrelated output").isEmpty());
    }
}
