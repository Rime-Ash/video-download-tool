package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserCookieSourceTest {
    @Test
    void reportsAppBoundEncryptionAsUnreadable() throws Exception {
        Path localAppData = Files.createTempDirectory("local-app-data");
        Path userData = Files.createDirectories(localAppData.resolve("Microsoft/Edge/User Data"));
        Files.writeString(userData.resolve("Local State"),
                "{\"os_crypt\":{\"app_bound_encrypted_key\":\"abc\"}}");

        BrowserCookieSource.Support support =
                BrowserCookieSource.check("edge", localAppData, Files.createTempDirectory("roaming"));

        assertFalse(support.readable());
        assertTrue(support.message().contains("应用绑定加密"));
        assertTrue(support.message().contains("Cookie 文件"));
    }

    @Test
    void reportsOlderChromiumProfileAsReadableWhenBrowserIsClosed() throws Exception {
        Path localAppData = Files.createTempDirectory("local-app-data");
        Path userData = Files.createDirectories(localAppData.resolve("Google/Chrome/User Data"));
        Files.writeString(userData.resolve("Local State"), "{\"os_crypt\":{\"encrypted_key\":\"abc\"}}");

        BrowserCookieSource.Support support =
                BrowserCookieSource.check("chrome", localAppData, Files.createTempDirectory("roaming"));

        assertTrue(support.readable());
        assertTrue(support.message().contains("完全退出浏览器"));
    }

    @Test
    void treatsFirefoxAsReadableAndMissingBrowsersAsUnavailable() throws Exception {
        Path localAppData = Files.createTempDirectory("local-app-data");
        Path roaming = Files.createTempDirectory("roaming");
        Files.createDirectories(roaming.resolve("Mozilla").resolve("Firefox"));

        assertTrue(BrowserCookieSource.check("firefox", localAppData, roaming).readable());
        assertFalse(BrowserCookieSource.check("brave", localAppData, roaming).readable());
    }

    @Test
    void ignoresEmptySelection() {
        assertTrue(BrowserCookieSource.check("   ").readable());
        assertTrue(BrowserCookieSource.check("   ").message().isEmpty());
        assertTrue(BrowserCookieSource.check(null).message().isEmpty());
    }
}
