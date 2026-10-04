package com.nzsk.videodownloader.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.ImageInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.ImagePostRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.util.ImageCdnHosts;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultImagePostClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger imageRequests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/aweme/v1/web/aweme/detail/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = query != null && query.contains("aweme_id=1111111111111111111")
                    ? "<html><title>验证码中间页</title></html>"
                    : imagePostJson(baseUrl);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.createContext("/img/", exchange -> {
            imageRequests.incrementAndGet();
            byte[] bytes = ("fake-image-" + exchange.getRequestURI().getPath())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
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
    void inspectsAndDownloadsEveryImageOfThePost() throws Exception {
        Path directory = Files.createTempDirectory("image-post");
        DefaultImagePostClient client = newClient(uri -> true);
        try {
            ValidatedUrl url = noteUrl("7300000000000000000");
            ImagePostInfo info = client.inspect(url, null);
            assertEquals(2, info.imageCount());
            assertEquals("本地作者", info.author());

            List<DownloadProgress> progress = new ArrayList<>();
            ImagePostRequest request = new ImagePostRequest(
                    url, info.postId(), "本地图集", info.images(), directory, null);
            DownloadHandle handle = client.download(request, progress::add);
            handle.awaitCompletion();

            assertTrue(handle.succeeded(), "下载应当成功");
            Path folder = directory.resolve("本地图集");
            assertTrue(Files.isRegularFile(folder.resolve("01.jpg")));
            assertTrue(Files.isRegularFile(folder.resolve("02.jpg")));
            assertTrue(Files.isRegularFile(folder.resolve("01.mp4")), "动图应同时保存短视频");
            assertEquals(3, imageRequests.get(), "2 张图片 + 1 段动图视频");
            assertEquals(folder.toAbsolutePath().normalize(), handle.outputFile().orElseThrow());
            assertEquals(100.0, progress.get(progress.size() - 1).percentage());
            assertEquals("2 / 2 张", progress.get(progress.size() - 1).downloadedSize());
        } finally {
            client.close();
        }
    }

    @Test
    void skipsImagesThatAlreadyExist() throws Exception {
        Path directory = Files.createTempDirectory("image-post-resume");
        Path folder = Files.createDirectories(directory.resolve("本地图集"));
        Files.writeString(folder.resolve("01.jpg"), "already-there");
        DefaultImagePostClient client = newClient(uri -> true);
        try {
            ImagePostRequest request = new ImagePostRequest(
                    noteUrl("7300000000000000000"),
                    "7300000000000000000",
                    "本地图集",
                    List.of(new ImageInfo(baseUrl + "/img/1.jpeg", "jpg", 800, 600),
                            new ImageInfo(baseUrl + "/img/2.jpeg", "jpg", 800, 600)),
                    directory,
                    null);
            DownloadHandle handle = client.download(request, progress -> { });
            handle.awaitCompletion();

            assertTrue(handle.succeeded());
            assertEquals("already-there", Files.readString(folder.resolve("01.jpg")));
            assertEquals(1, imageRequests.get(), "已存在的图片不应重复下载");
        } finally {
            client.close();
        }
    }

    @Test
    void refusesImageUrlsOutsideTheMediaCdnAllowlist() throws Exception {
        Path directory = Files.createTempDirectory("image-post-policy");
        DefaultImagePostClient client = newClient(ImageCdnHosts::isAllowed);
        try {
            ImagePostRequest request = new ImagePostRequest(
                    noteUrl("7300000000000000000"),
                    "7300000000000000000",
                    "本地图集",
                    List.of(new ImageInfo(baseUrl + "/img/1.jpeg", "jpg", 800, 600)),
                    directory,
                    null);
            DownloadHandle handle = client.download(request, progress -> { });
            handle.awaitCompletion();

            assertFalse(handle.succeeded());
            assertTrue(handle.failureMessage().orElseThrow().contains("CDN"));
            assertEquals(0, imageRequests.get());
        } finally {
            client.close();
        }
    }

    @Test
    void reportsVerificationPageInsteadOfCrashing() {
        DefaultImagePostClient client = newClient(uri -> true);
        try {
            ImagePostException failure = assertThrows(ImagePostException.class,
                    () -> client.inspect(noteUrl("1111111111111111111"), null));
            assertTrue(failure.getMessage().contains("安全验证"));
        } finally {
            client.close();
        }
    }

    private static String imagePostJson(String base) {
        return """
                {"status_code":0,"aweme_detail":{"aweme_id":"7300000000000000000","desc":"本地图集",
                "aweme_type":68,"author":{"nickname":"本地作者"},"images":[
                {"width":800,"height":600,"url_list":["%s/img/1.jpeg"],
                 "video":{"duration":3000,"width":800,"height":600,
                          "play_addr":{"url_list":["%s/img/1.mp4"]}}},
                {"width":600,"height":800,"url_list":["%s/img/2.jpeg"]}]}}
                """.formatted(base, base, base);
    }

    private DefaultImagePostClient newClient(java.util.function.Predicate<URI> imageHostPolicy) {
        return new DefaultImagePostClient(new ObjectMapper(), HttpClient.newHttpClient(),
                baseUrl + "/aweme/v1/web/aweme/detail/", imageHostPolicy);
    }

    private static ValidatedUrl noteUrl(String postId) {
        return new ValidatedUrl(URI.create("https://www.douyin.com/note/" + postId), "www.douyin.com");
    }
}
