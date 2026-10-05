package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.DouyinVideoException;
import com.nzsk.videodownloader.model.DouyinVideoFormat;
import com.nzsk.videodownloader.model.DouyinVideoRequest;
import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultDouyinVideoClientTest {
    private static final int PAYLOAD_BYTES = 64 * 1024;
    private static final String POST_ID = "7300000000000000000";

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger mediaRequests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/media/video.mp4", exchange -> {
            mediaRequests.incrementAndGet();
            byte[] payload = new byte[PAYLOAD_BYTES];
            Arrays.fill(payload, (byte) 7);
            exchange.getResponseHeaders().add("Content-Type", "video/mp4");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void downloadsResolvedMediaAndReportsProgress() throws Exception {
        Path directory = Files.createTempDirectory("douyin-video");
        DefaultDouyinVideoClient client = newClient(uri -> true);
        try {
            List<DownloadProgress> progress = new ArrayList<>();
            DownloadHandle handle = client.download(request(directory), progress::add);
            handle.awaitCompletion();

            assertTrue(handle.succeeded(), handle.failureMessage().orElse("下载应当成功"));
            Path file = directory.resolve("本地视频.mp4");
            assertTrue(Files.isRegularFile(file));
            assertEquals(PAYLOAD_BYTES, Files.size(file));
            assertEquals(1, mediaRequests.get());
            assertEquals(100.0, progress.get(progress.size() - 1).percentage());
            assertEquals(file.toAbsolutePath().normalize(), handle.outputFile().orElseThrow());
        } finally {
            client.close();
        }
    }

    @Test
    void skipsMediaThatWasAlreadyDownloaded() throws Exception {
        Path directory = Files.createTempDirectory("douyin-video-resume");
        Path existing = Files.writeString(directory.resolve("本地视频.mp4"), "already-there");
        DefaultDouyinVideoClient client = newClient(uri -> true);
        try {
            DownloadHandle handle = client.download(request(directory), progress -> { });
            handle.awaitCompletion();

            assertTrue(handle.succeeded());
            assertEquals("already-there", Files.readString(existing));
            assertEquals(0, mediaRequests.get(), "已存在的文件不应重复下载");
        } finally {
            client.close();
        }
    }

    @Test
    void refusesMediaOutsideTheCdnAllowlist() throws Exception {
        Path directory = Files.createTempDirectory("douyin-video-policy");
        DefaultDouyinVideoClient client = newClient(uri -> false);
        try {
            DownloadHandle handle = client.download(request(directory), progress -> { });
            handle.awaitCompletion();

            assertFalse(handle.succeeded());
            assertTrue(handle.failureMessage().orElseThrow().contains("CDN"));
            assertEquals(0, mediaRequests.get());
        } finally {
            client.close();
        }
    }

    @Test
    void explainsThatNoBrowserIsAvailable() throws Exception {
        Path directory = Files.createTempDirectory("douyin-video-browser");
        Path missingBrowser = directory.resolve("missing-browser.exe");
        DefaultDouyinVideoClient client = new DefaultDouyinVideoClient(
                new DefaultProcessExecutor(), HttpClient.newHttpClient(), missingBrowser, uri -> true);
        try {
            DouyinVideoException failure = assertThrows(DouyinVideoException.class,
                    () -> client.inspect(videoUrl(), null));
            assertTrue(failure.getMessage().contains("浏览器"));
        } finally {
            client.close();
        }
    }

    private DefaultDouyinVideoClient newClient(java.util.function.Predicate<URI> mediaHostPolicy) {
        return new DefaultDouyinVideoClient(new DefaultProcessExecutor(), HttpClient.newHttpClient(),
                Path.of("unused-browser.exe"), mediaHostPolicy);
    }

    private DouyinVideoRequest request(Path directory) {
        return new DouyinVideoRequest(
                videoUrl(),
                POST_ID,
                "本地视频",
                new DouyinVideoFormat(
                        "源 1", baseUrl + "/media/video.mp4", "mp4", "浏览器解析（CDN 直链）"),
                directory,
                null);
    }

    private static ValidatedUrl videoUrl() {
        return new ValidatedUrl(
                URI.create("https://www.douyin.com/video/" + POST_ID), "www.douyin.com");
    }
}
