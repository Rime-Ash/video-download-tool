package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetscapeCookieStoreTest {
    private static final long FUTURE = 2_000_000_000L;
    private static final long PAST = 1_000_000_000L;

    @Test
    void buildsHeaderFromMatchingDomainsOnly() throws Exception {
        Path file = writeCookies("""
                # Netscape HTTP Cookie File
                .douyin.com\tTRUE\t/\tTRUE\t%d\tttwid\tabc123
                #HttpOnly_.douyin.com\tTRUE\t/\tTRUE\t%d\tsessionid\tsecret456
                .example.com\tTRUE\t/\tFALSE\t%d\tforeign\tignored
                """.formatted(FUTURE, FUTURE, FUTURE));

        String header = NetscapeCookieStore.cookieHeader(file, URI.create("https://www.douyin.com/"))
                .orElseThrow();

        assertTrue(header.contains("ttwid=abc123"));
        assertTrue(header.contains("sessionid=secret456"));
        assertFalse(header.contains("foreign"));
    }

    @Test
    void skipsExpiredCookiesAndUnknownHosts() throws Exception {
        Path file = writeCookies("""
                .douyin.com\tTRUE\t/\tTRUE\t%d\tstale\told
                .douyin.com\tTRUE\t/\tTRUE\t%d\tfresh\tnew
                """.formatted(PAST, FUTURE));

        String header = NetscapeCookieStore.cookieHeader(file, URI.create("https://www.douyin.com/"))
                .orElseThrow();

        assertFalse(header.contains("stale"));
        assertTrue(header.contains("fresh=new"));
        assertTrue(NetscapeCookieStore.cookieHeader(file, URI.create("https://www.bilibili.com/"))
                .isEmpty());
    }

    @Test
    void toleratesHttpOnlyPrefixAndMissingFile() throws Exception {
        Path file = writeCookies(
                "#HttpOnly_.douyin.com\tTRUE\t/\tTRUE\t" + FUTURE + "\tttwid\tvalue\n");

        assertEquals("ttwid=value",
                NetscapeCookieStore.cookieHeader(file, URI.create("https://www.douyin.com")).orElseThrow());
        assertTrue(NetscapeCookieStore.cookieHeader(
                Path.of("missing-cookies-file.txt"), URI.create("https://www.douyin.com")).isEmpty());
    }

    private static Path writeCookies(String content) throws Exception {
        Path file = Files.createTempFile("video-downloader-cookies", ".txt");
        Files.writeString(file, content);
        return file;
    }
}
