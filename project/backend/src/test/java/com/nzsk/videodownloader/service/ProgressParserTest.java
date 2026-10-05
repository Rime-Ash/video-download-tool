package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.DownloadState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressParserTest {
    @Test
    void parsesStructuredProgressLine() {
        var progress = ProgressParser.parse(
                ProgressParser.PROGRESS_MARKER + "42.5%|1024|1.2MiB/s|00:10");

        assertTrue(progress.isPresent());
        assertEquals(42.5, progress.orElseThrow().percentage());
        assertEquals("1024", progress.orElseThrow().downloadedSize());
        assertEquals("1.2MiB/s", progress.orElseThrow().speed());
        assertEquals("00:10", progress.orElseThrow().eta());
        assertEquals(DownloadState.DOWNLOADING, progress.orElseThrow().state());
    }

    /** Real yt-dlp output keeps the padding of {@code _percent_str} and prints {@code NA} for unknown fields. */
    @Test
    void parsesRawYtDlpProgressLine() {
        var progress = ProgressParser.parse(
                ProgressParser.PROGRESS_MARKER + "  0.0%| 1.00KiB/N/A|NA|NA");

        assertTrue(progress.isPresent());
        assertEquals(0.0, progress.orElseThrow().percentage());
        assertEquals("1.00KiB", progress.orElseThrow().downloadedSize());
        assertEquals("", progress.orElseThrow().speed());
        assertEquals("", progress.orElseThrow().eta());
    }

    @Test
    void keepsHumanReadableSizeSpeedAndEta() {
        var progress = ProgressParser.parse(
                ProgressParser.PROGRESS_MARKER + " 26.0%|1023.00KiB/   2.00MiB|4.88MiB/s|00:03");

        assertEquals("1023.00KiB/2.00MiB", progress.orElseThrow().downloadedSize());
        assertEquals("4.88MiB/s", progress.orElseThrow().speed());
        assertEquals("00:03", progress.orElseThrow().eta());
    }

    @Test
    void ignoresNonProgressOutput() {
        assertTrue(ProgressParser.parse("WARNING: unrelated output").isEmpty());
    }

    /**
     * The template prefix {@code download:} is a yt-dlp type selector, so output without the embedded
     * marker must not be treated as progress.
     */
    @Test
    void ignoresLinesWithoutMarker() {
        assertTrue(ProgressParser.parse("  0.0%|1024|NA|NA").isEmpty());
        assertTrue(ProgressParser.parse("download:42.5%|1024|1.2MiB/s|00:10").isEmpty());
    }
}
