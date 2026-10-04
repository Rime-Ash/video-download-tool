package com.nzsk.videodownloader.model;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadOptionsTest {
    @Test
    void normalizesBlankValues() {
        DownloadOptions options = new DownloadOptions("  ", null, null, null, null);

        assertNull(options.rateLimit());
        assertNull(options.proxy());
        assertNull(options.cookieBrowser());
    }

    @Test
    void trimsRateLimitAndAcceptsUnitsInEitherCase() {
        assertEquals("1.5M", new DownloadOptions(" 1.5M ", null, null, null, null).rateLimit());
        assertEquals("500k", new DownloadOptions("500k", null, null, null, null).rateLimit());
    }

    @Test
    void rejectsNonHttpProxySchemes() {
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadOptions(null, URI.create("socks5://127.0.0.1:1080"), null, null, null));
    }

    @Test
    void acceptsKnownBrowserCookieSourcesAndRejectsOthers() {
        assertEquals("edge", new DownloadOptions(null, null, null, null, " Edge ").cookieBrowser());
        assertEquals("firefox:Profile 1",
                new DownloadOptions(null, null, null, null, "firefox:Profile 1").cookieBrowser());
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadOptions(null, null, null, null, "chrome; del *"));
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadOptions(null, null, null, null, "C:\\temp\\profile"));
    }
}
