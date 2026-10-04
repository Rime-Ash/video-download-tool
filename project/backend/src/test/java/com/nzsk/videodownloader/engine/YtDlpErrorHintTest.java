package com.nzsk.videodownloader.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YtDlpErrorHintTest {
    @Test
    void suggestsCookiesWhenYtDlpAsksForThem() {
        String hint = DefaultYtDlpClient.hintFor(
                "ERROR: [Douyin] 7300000000000000000: Fresh cookies (not necessarily logged in) are needed");

        assertTrue(hint.contains("Cookie"));
    }

    @Test
    void explainsProfileLinksWhenTheUrlIsUnsupported() {
        String hint = DefaultYtDlpClient.hintFor(
                "ERROR: Unsupported URL: https://www.douyin.com/user/MS4wLjABAAAA");

        assertTrue(hint.contains("单个视频"));
    }

    @Test
    void neverEchoesTheRawOutput() {
        String hint = DefaultYtDlpClient.hintFor(
                "ERROR: unable to extract; cookies at C:/secret/cookies.txt failed");

        assertFalse(hint.contains("secret"));
        assertFalse(hint.contains("C:/"));
    }

    @Test
    void explainsAppBoundEncryptionFailures() {
        String hint = DefaultYtDlpClient.hintFor(
                "ERROR: Failed to decrypt with DPAPI. See https://github.com/yt-dlp/yt-dlp/issues/10927");

        assertTrue(hint.contains("应用绑定加密"));
        assertTrue(hint.contains("Cookie 文件"));
    }

    @Test
    void explainsLockedCookieDatabase() {
        String hint = DefaultYtDlpClient.hintFor(
                "ERROR: Could not copy Chrome cookie database. See https://github.com/yt-dlp/yt-dlp/issues/7271");

        assertTrue(hint.contains("完全退出浏览器"));
        assertFalse(hint.contains("github.com"));
    }
}
