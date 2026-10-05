package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.engine.DownloadHandle;
import com.nzsk.videodownloader.engine.DouyinVideoClient;
import com.nzsk.videodownloader.engine.ImagePostClient;
import com.nzsk.videodownloader.engine.ProgressListener;
import com.nzsk.videodownloader.engine.YtDlpClient;
import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.exception.ImagePostException;
import com.nzsk.videodownloader.model.DouyinVideoFormat;
import com.nzsk.videodownloader.model.DouyinVideoInfo;
import com.nzsk.videodownloader.model.DouyinVideoRequest;
import com.nzsk.videodownloader.model.DownloadOptions;
import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.DownloadState;
import com.nzsk.videodownloader.model.DownloadTaskId;
import com.nzsk.videodownloader.model.ImageInfo;
import com.nzsk.videodownloader.model.ImagePostInfo;
import com.nzsk.videodownloader.model.ImagePostRequest;
import com.nzsk.videodownloader.model.ValidatedUrl;
import com.nzsk.videodownloader.model.VideoInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadQueueTest {
    @Test
    void completesSuccessfulTaskAndPublishesProgress() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        CountDownLatch started = new CountDownLatch(1);
        YtDlpClient client = new FakeClient(started, true);
        var queue = newQueue(client, 1, new RetryService(1, ignored -> { }));
        var task = queue.addTask(request(directory));

        queue.start(task.id());

        assertTrue(started.await(2, TimeUnit.SECONDS));
        awaitState(queue, task.id(), DownloadState.COMPLETED);
        var completed = queue.get(task.id()).orElseThrow();
        assertEquals(DownloadState.COMPLETED, completed.state());
        assertEquals(100, completed.progress().percentage());
        assertEquals(directory.resolve("video.mp4").toAbsolutePath().normalize(),
                completed.outputFile());
        queue.close();
    }

    @Test
    void cancelMarksTaskCancelled() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        CountDownLatch started = new CountDownLatch(1);
        YtDlpClient client = new FakeClient(started, false);
        var queue = newQueue(client, 1, new RetryService(1, ignored -> { }));
        var task = queue.addTask(request(directory));
        queue.start(task.id());
        assertTrue(started.await(2, TimeUnit.SECONDS));

        queue.cancel(task.id());

        assertEquals(DownloadState.CANCELLED, queue.get(task.id()).orElseThrow().state());
        queue.close();
    }

    @Test
    void pauseAndResumeReturnTaskToQueue() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        CountDownLatch started = new CountDownLatch(1);
        YtDlpClient client = new FakeClient(started, false);
        var queue = newQueue(client, 1, new RetryService(1, ignored -> { }));
        var task = queue.addTask(request(directory));
        queue.start(task.id());
        assertTrue(started.await(2, TimeUnit.SECONDS));

        queue.pause(task.id());

        awaitState(queue, task.id(), DownloadState.PAUSED);
        queue.resume(task.id());
        awaitStateIn(queue, task.id(), DownloadState.QUEUED, DownloadState.DOWNLOADING);
        queue.close();
    }

    @Test
    void retryReturnsCancelledTaskToQueue() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        CountDownLatch started = new CountDownLatch(1);
        var queue = newQueue(new FakeClient(started, false), 1,
                new RetryService(1, ignored -> { }));
        var task = queue.addTask(request(directory));
        queue.start(task.id());
        assertTrue(started.await(2, TimeUnit.SECONDS));

        queue.cancel(task.id());
        assertEquals(DownloadState.CANCELLED, queue.get(task.id()).orElseThrow().state());
        queue.retry(task.id());

        awaitStateIn(queue, task.id(), DownloadState.QUEUED, DownloadState.DOWNLOADING);
        queue.close();
    }

    @Test
    void respectsConfiguredConcurrencyLimit() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        var client = new CountingClient();
        var queue = newQueue(client, 1, new RetryService(1, ignored -> { }));
        var first = queue.addTask(request(directory));
        var second = queue.addTask(request(directory));

        queue.start(first.id());
        queue.start(second.id());
        assertTrue(client.firstStarted.await(2, TimeUnit.SECONDS));
        Thread.sleep(150);
        assertEquals(1, client.startedCount.get());

        client.releaseFirst.countDown();
        awaitState(queue, first.id(), DownloadState.COMPLETED);
        awaitState(queue, second.id(), DownloadState.COMPLETED);
        assertEquals(2, client.startedCount.get());
        queue.close();
    }

    @Test
    void rejectsInvalidConcurrencyLimit() {
        var queue = newQueue(new FakeClient(new CountDownLatch(1), true), 1,
                new RetryService(1, ignored -> { }));

        assertThrows(IllegalArgumentException.class, () -> queue.setMaxConcurrentDownloads(0));
        queue.close();
    }

    @Test
    void retriesAttemptThatFailsAfterStarting() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        var client = new FlakyClient(2);
        var queue = newQueue(client, 1, new RetryService(3, ignored -> { }));
        var task = queue.addTask(request(directory));

        queue.start(task.id());

        awaitState(queue, task.id(), DownloadState.COMPLETED);
        assertEquals(2, client.attempts.get());
        queue.close();
    }

    @Test
    void reportsFailureAfterAllAttempts() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue");
        var client = new FlakyClient(Integer.MAX_VALUE);
        var queue = newQueue(client, 1, new RetryService(2, ignored -> { }));
        var task = queue.addTask(request(directory));

        queue.start(task.id());

        awaitState(queue, task.id(), DownloadState.FAILED);
        assertEquals(2, client.attempts.get());
        assertTrue(queue.get(task.id()).orElseThrow().errorMessage().contains("第 2 次尝试"));
        queue.close();
    }

    private static DownloadRequest request(Path directory) {
        return new DownloadRequest(
                new ValidatedUrl(URI.create("https://www.bilibili.com/video/BV1"), "www.bilibili.com"),
                "best", directory, "video", true, DownloadOptions.NONE);
    }

    private static DownloadQueue newQueue(YtDlpClient client, int maxConcurrent, RetryService retry) {
        return new DownloadQueue(client, new NoopImagePostClient(), maxConcurrent, retry);
    }

    @Test
    void completesImagePostTaskAndReportsFolder() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue-images");
        var imageClient = new FakeImagePostClient(directory);
        var queue = new DownloadQueue(new FakeClient(new CountDownLatch(1), true), imageClient, 1,
                new RetryService(1, ignored -> { }));
        var task = queue.addImagePostTask(imagePostRequest(directory));

        queue.start(task.id());

        awaitState(queue, task.id(), DownloadState.COMPLETED);
        var completed = queue.get(task.id()).orElseThrow();
        assertEquals("图集 2 张", completed.summary());
        assertEquals(directory.resolve("测试图集").toAbsolutePath().normalize(), completed.outputFile());
        queue.close();
        imageClient.close();
    }

    private static ImagePostRequest imagePostRequest(Path directory) {
        return new ImagePostRequest(
                new ValidatedUrl(URI.create("https://www.douyin.com/note/7300000000000000000"),
                        "www.douyin.com"),
                "7300000000000000000",
                "测试图集",
                java.util.List.of(
                        new ImageInfo("https://p3-pc-sign.douyinpic.com/a.jpeg", "jpg", 1920, 1080),
                        new ImageInfo("https://p3-pc-sign.douyinpic.com/b.jpeg", "jpg", 1920, 1080)),
                directory,
                null);
    }

    @Test
    void completesDouyinFallbackTaskAndReportsFile() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue-douyin");
        var douyinClient = new FakeDouyinVideoClient(directory);
        var queue = newQueue(new FakeClient(new CountDownLatch(1), true), 1,
                new RetryService(1, ignored -> { }));
        queue.setDouyinVideoClient(douyinClient);
        var task = queue.addDouyinVideoTask(douyinVideoRequest(directory));

        queue.start(task.id());

        awaitState(queue, task.id(), DownloadState.COMPLETED);
        var completed = queue.get(task.id()).orElseThrow();
        assertEquals("本地视频.mp4", completed.fileName());
        assertEquals("浏览器解析（CDN 直链）", completed.summary());
        assertEquals(directory.resolve("本地视频.mp4").toAbsolutePath().normalize(),
                completed.outputFile());
        queue.close();
        douyinClient.close();
    }

    @Test
    void rejectsDouyinTaskWhenTheFallbackIsNotConfigured() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-queue-douyin-none");
        var queue = newQueue(new FakeClient(new CountDownLatch(1), true), 1,
                new RetryService(1, ignored -> { }));

        assertThrows(IllegalStateException.class,
                () -> queue.addDouyinVideoTask(douyinVideoRequest(directory)));
        queue.close();
    }

    private static DouyinVideoRequest douyinVideoRequest(Path directory) {
        return new DouyinVideoRequest(
                new ValidatedUrl(URI.create("https://www.douyin.com/video/7300000000000000000"),
                        "www.douyin.com"),
                "7300000000000000000",
                "本地视频",
                new DouyinVideoFormat(
                        "源 1", "https://v26-web.douyinvod.com/x/video/tos/cn/y/", "mp4",
                        "浏览器解析（CDN 直链）"),
                directory,
                null);
    }

    /** Douyin fallback downloads are exercised by DefaultDouyinVideoClientTest; only the queue matters. */
    private static final class FakeDouyinVideoClient implements DouyinVideoClient, AutoCloseable {
        private final Path directory;

        private FakeDouyinVideoClient(Path directory) {
            this.directory = directory;
        }

        @Override
        public DouyinVideoInfo inspect(ValidatedUrl url, Path cookieFile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadHandle download(DouyinVideoRequest request, ProgressListener progressListener)
                throws DownloadException {
            Path output = directory.resolve(request.baseFileName() + ".mp4")
                    .toAbsolutePath().normalize();
            progressListener.onProgress(new DownloadProgress(
                    50, "1 MiB", "1 MiB/s", "00:01", DownloadState.DOWNLOADING));
            return new DownloadHandle() {
                @Override
                public void pause() {
                }

                @Override
                public void cancel() {
                }

                @Override
                public void awaitCompletion() {
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public Optional<Path> outputFile() {
                    return Optional.of(output);
                }
            };
        }

        @Override
        public void close() {
        }
    }

    /** Image post downloads are exercised by DefaultImagePostClientTest; here only the queue matters. */
    private static final class FakeImagePostClient implements ImagePostClient, AutoCloseable {
        private final Path folder;

        private FakeImagePostClient(Path folder) {
            this.folder = folder;
        }

        @Override
        public ImagePostInfo inspect(ValidatedUrl url, Path cookieFile) throws ImagePostException {
            throw new ImagePostException("图集解析不在本测试范围内。");
        }

        @Override
        public DownloadHandle download(ImagePostRequest request, ProgressListener progressListener)
                throws DownloadException {
            Path output = folder.resolve(request.title()).toAbsolutePath().normalize();
            progressListener.onProgress(new DownloadProgress(
                    50, "1 / 2 张", "", "", DownloadState.DOWNLOADING));
            return new DownloadHandle() {
                @Override
                public void pause() {
                }

                @Override
                public void cancel() {
                }

                @Override
                public void awaitCompletion() {
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public java.util.Optional<Path> outputFile() {
                    return java.util.Optional.of(output);
                }
            };
        }

        @Override
        public void close() {
        }
    }

    private static final class NoopImagePostClient implements ImagePostClient {
        @Override
        public ImagePostInfo inspect(ValidatedUrl url, Path cookieFile) throws ImagePostException {
            throw new ImagePostException("本测试不使用图集功能。");
        }

        @Override
        public DownloadHandle download(ImagePostRequest request, ProgressListener progressListener)
                throws DownloadException {
            throw new DownloadException("本测试不使用图集功能。");
        }
    }

    private static void awaitState(DownloadQueue queue, DownloadTaskId id, DownloadState expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (queue.get(id).orElseThrow().state() == expected) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals(expected, queue.get(id).orElseThrow().state());
    }

    private static void awaitStateIn(DownloadQueue queue, DownloadTaskId id, DownloadState... expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            DownloadState current = queue.get(id).orElseThrow().state();
            for (DownloadState candidate : expected) {
                if (current == candidate) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("unexpected state: " + queue.get(id).orElseThrow().state());
    }

    private static final class FakeClient implements YtDlpClient {
        private final CountDownLatch started;
        private final boolean successful;

        private FakeClient(CountDownLatch started, boolean successful) {
            this.started = started;
            this.successful = successful;
        }

        @Override
        public VideoInfo inspect(ValidatedUrl url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadHandle download(DownloadRequest request, ProgressListener progressListener) {
            started.countDown();
            progressListener.onProgress(new DownloadProgress(
                    50, "1 MiB", "1 MiB/s", "00:01", DownloadState.DOWNLOADING));
            Path outputFile = request.downloadDirectory().resolve(request.baseFileName() + ".mp4")
                    .toAbsolutePath().normalize();
            return new DownloadHandle() {
                private volatile boolean cancelled;

                @Override
                public void pause() {
                    cancelled = true;
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public void awaitCompletion() throws InterruptedException {
                    if (!successful) {
                        while (!cancelled) {
                            Thread.sleep(10);
                        }
                    }
                }

                @Override
                public boolean succeeded() {
                    return successful && !cancelled;
                }

                @Override
                public Optional<Path> outputFile() {
                    return successful ? Optional.of(outputFile) : Optional.empty();
                }
            };
        }
    }

    private static final class CountingClient implements YtDlpClient {
        private final AtomicInteger startedCount = new AtomicInteger();
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch releaseFirst = new CountDownLatch(1);

        @Override
        public VideoInfo inspect(ValidatedUrl url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadHandle download(DownloadRequest request, ProgressListener progressListener) {
            int index = startedCount.incrementAndGet();
            if (index == 1) {
                firstStarted.countDown();
            }
            return new DownloadHandle() {
                @Override
                public void pause() {
                }

                @Override
                public void cancel() {
                }

                @Override
                public void awaitCompletion() throws InterruptedException {
                    if (index == 1) {
                        releaseFirst.await(3, TimeUnit.SECONDS);
                    }
                }

                @Override
                public boolean succeeded() {
                    return true;
                }
            };
        }
    }

    /** Fails every attempt before {@code firstSuccessfulAttempt} and succeeds afterwards. */
    private static final class FlakyClient implements YtDlpClient {
        private final int firstSuccessfulAttempt;
        private final AtomicInteger attempts = new AtomicInteger();

        private FlakyClient(int firstSuccessfulAttempt) {
            this.firstSuccessfulAttempt = firstSuccessfulAttempt;
        }

        @Override
        public VideoInfo inspect(ValidatedUrl url) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DownloadHandle download(DownloadRequest request, ProgressListener progressListener) {
            int attempt = attempts.incrementAndGet();
            Path outputFile = request.downloadDirectory().resolve(request.baseFileName() + ".mp4")
                    .toAbsolutePath().normalize();
            return new DownloadHandle() {
                @Override
                public void pause() {
                }

                @Override
                public void cancel() {
                }

                @Override
                public void awaitCompletion() {
                }

                @Override
                public boolean succeeded() {
                    return attempt >= firstSuccessfulAttempt;
                }

                @Override
                public Optional<Path> outputFile() {
                    return attempt >= firstSuccessfulAttempt ? Optional.of(outputFile) : Optional.empty();
                }
            };
        }
    }
}
