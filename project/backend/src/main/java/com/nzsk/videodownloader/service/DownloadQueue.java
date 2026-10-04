package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.engine.DownloadHandle;
import com.nzsk.videodownloader.engine.DownloadJob;
import com.nzsk.videodownloader.engine.ImagePostClient;
import com.nzsk.videodownloader.engine.ImagePostDownloadJob;
import com.nzsk.videodownloader.engine.VideoDownloadJob;
import com.nzsk.videodownloader.engine.YtDlpClient;
import com.nzsk.videodownloader.exception.DownloadException;
import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.DownloadRequest;
import com.nzsk.videodownloader.model.DownloadState;
import com.nzsk.videodownloader.model.DownloadTask;
import com.nzsk.videodownloader.model.DownloadTaskId;
import com.nzsk.videodownloader.model.DownloadTaskKind;
import com.nzsk.videodownloader.model.ImagePostRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

/**
 * Queue for both video downloads and image post downloads.
 *
 * <p>Every entry is described by a {@link DownloadJob}, so queueing, progress reporting, pause, cancel and
 * retry behave the same for both media kinds.</p>
 */
public final class DownloadQueue implements DownloadService, AutoCloseable {
    private final RetryService retryService;
    private final ExecutorService workers;
    private final Object stateLock = new Object();
    private final Map<DownloadTaskId, DownloadTask> tasks = new LinkedHashMap<>();
    private final Map<DownloadTaskId, DownloadJob> jobs = new LinkedHashMap<>();
    private final Map<DownloadTaskId, DownloadHandle> handles = new ConcurrentHashMap<>();

    private YtDlpClient ytDlpClient;
    private ImagePostClient imagePostClient;
    private int maxConcurrentDownloads;
    private int activeDownloads;

    public DownloadQueue(YtDlpClient ytDlpClient, ImagePostClient imagePostClient,
                         int maxConcurrentDownloads, RetryService retryService) {
        setYtDlpClient(ytDlpClient);
        setImagePostClient(imagePostClient);
        this.retryService = Objects.requireNonNull(retryService, "retryService");
        setMaxConcurrentDownloads(maxConcurrentDownloads);
        this.workers = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "video-downloader-task");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Replaces the yt-dlp client, for example after the tool path was changed in settings. */
    public void setYtDlpClient(YtDlpClient client) {
        this.ytDlpClient = Objects.requireNonNull(client, "ytDlpClient");
    }

    public void setImagePostClient(ImagePostClient client) {
        this.imagePostClient = Objects.requireNonNull(client, "imagePostClient");
    }

    @Override
    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) {
        if (maxConcurrentDownloads < 1) {
            throw new IllegalArgumentException("maxConcurrentDownloads must be positive");
        }
        synchronized (stateLock) {
            this.maxConcurrentDownloads = maxConcurrentDownloads;
            stateLock.notifyAll();
        }
    }

    @Override
    public DownloadTask addTask(DownloadRequest request) {
        Objects.requireNonNull(request, "request");
        return enqueue(new VideoDownloadJob(ytDlpClient, request), DownloadTaskKind.VIDEO);
    }

    @Override
    public DownloadTask addImagePostTask(ImagePostRequest request) {
        Objects.requireNonNull(request, "request");
        return enqueue(new ImagePostDownloadJob(imagePostClient, request), DownloadTaskKind.IMAGE_POST);
    }

    private DownloadTask enqueue(DownloadJob job, DownloadTaskKind kind) {
        DownloadTask task = new DownloadTask(
                DownloadTaskId.create(), kind, job.fileName(), job.summary(), DownloadState.QUEUED,
                new DownloadProgress(0, "", "", "", DownloadState.QUEUED), null, null);
        synchronized (stateLock) {
            tasks.put(task.id(), task);
            jobs.put(task.id(), job);
        }
        return task;
    }

    @Override
    public void start(DownloadTaskId taskId) {
        DownloadTask task = requireTask(taskId);
        if (task.state() != DownloadState.QUEUED && task.state() != DownloadState.FAILED) {
            return;
        }
        update(taskId, DownloadState.DOWNLOADING,
                new DownloadProgress(task.progress().percentage(), task.progress().downloadedSize(),
                        task.progress().speed(), task.progress().eta(), DownloadState.DOWNLOADING), null);
        workers.submit(() -> runTask(task.id()));
    }

    private void runTask(DownloadTaskId taskId) {
        boolean acquired = false;
        try {
            acquireSlot();
            acquired = true;
            DownloadState requested = currentState(taskId);
            if (requested == DownloadState.CANCELLED || requested == DownloadState.PAUSED) {
                return;
            }
            DownloadJob job = requireJob(taskId);
            DownloadHandle handle = retryService.execute(attempt -> runAttempt(taskId, job, attempt));
            DownloadState latestState = currentState(taskId);
            if (latestState == DownloadState.CANCELLED || latestState == DownloadState.PAUSED) {
                return;
            }
            handle.outputFile().ifPresent(path -> updateOutputFile(taskId, path));
            update(taskId, DownloadState.COMPLETED,
                    new DownloadProgress(100, "", "", "", DownloadState.COMPLETED), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            updateFailure(taskId, "下载任务被中断。");
        } catch (Exception exception) {
            updateFailure(taskId, userMessage(exception));
        } finally {
            handles.remove(taskId);
            if (acquired) {
                releaseSlot();
            }
        }
    }

    /**
     * Runs one attempt and waits for it, so that a job failing after it started is also retried with the
     * configured backoff instead of being reported as a permanent failure.
     */
    private DownloadHandle runAttempt(DownloadTaskId taskId, DownloadJob job, int attempt)
            throws DownloadException, InterruptedException {
        DownloadHandle handle = job.start(progress -> updateProgress(taskId, progress));
        handles.put(taskId, handle);
        DownloadState requestedState = currentState(taskId);
        if (requestedState == DownloadState.CANCELLED) {
            handle.cancel();
            return handle;
        }
        if (requestedState == DownloadState.PAUSED) {
            handle.pause();
            return handle;
        }
        handle.awaitCompletion();
        DownloadState afterAttempt = currentState(taskId);
        if (afterAttempt == DownloadState.CANCELLED || afterAttempt == DownloadState.PAUSED) {
            return handle;
        }
        if (!handle.succeeded()) {
            throw new DownloadException(handle.failureMessage().orElse(
                    "下载失败（第 " + attempt + " 次尝试），请检查网络连接、工具版本和保存目录权限。"));
        }
        return handle;
    }

    private void acquireSlot() throws InterruptedException {
        synchronized (stateLock) {
            while (activeDownloads >= maxConcurrentDownloads) {
                stateLock.wait();
            }
            activeDownloads++;
        }
    }

    private void releaseSlot() {
        synchronized (stateLock) {
            if (activeDownloads > 0) {
                activeDownloads--;
            }
            stateLock.notifyAll();
        }
    }

    @Override
    public void pause(DownloadTaskId taskId) {
        DownloadHandle handle = handles.get(taskId);
        if (handle != null) {
            handle.pause();
        }
        mutate(taskId, task -> switch (task.state()) {
            case QUEUED, PARSING, DOWNLOADING, MERGING -> withState(task, DownloadState.PAUSED);
            default -> task;
        });
    }

    @Override
    public void resume(DownloadTaskId taskId) {
        mutate(taskId, task -> task.state() == DownloadState.PAUSED
                ? withState(task, DownloadState.QUEUED)
                : task);
        start(taskId);
    }

    @Override
    public void cancel(DownloadTaskId taskId) {
        DownloadHandle handle = handles.get(taskId);
        if (handle != null) {
            handle.cancel();
        }
        mutate(taskId, task -> switch (task.state()) {
            case QUEUED, PARSING, DOWNLOADING, MERGING, PAUSED ->
                    withState(task, DownloadState.CANCELLED);
            default -> task;
        });
    }

    @Override
    public void retry(DownloadTaskId taskId) {
        mutate(taskId, task -> switch (task.state()) {
            case FAILED, CANCELLED -> withState(task, DownloadState.QUEUED);
            default -> task;
        });
        start(taskId);
    }

    @Override
    public void removeCompletedTasks() {
        synchronized (stateLock) {
            List<DownloadTaskId> completed = tasks.values().stream()
                    .filter(task -> task.state() == DownloadState.COMPLETED)
                    .map(DownloadTask::id)
                    .toList();
            completed.forEach(taskId -> {
                tasks.remove(taskId);
                jobs.remove(taskId);
                handles.remove(taskId);
            });
        }
    }

    public Optional<DownloadTask> get(DownloadTaskId taskId) {
        synchronized (stateLock) {
            return Optional.ofNullable(tasks.get(taskId));
        }
    }

    public List<DownloadTask> snapshot() {
        synchronized (stateLock) {
            return List.copyOf(new ArrayList<>(tasks.values()));
        }
    }

    private DownloadTask requireTask(DownloadTaskId taskId) {
        DownloadTask task;
        synchronized (stateLock) {
            task = tasks.get(taskId);
        }
        if (task == null) {
            throw new IllegalArgumentException("下载任务不存在。");
        }
        return task;
    }

    private DownloadJob requireJob(DownloadTaskId taskId) {
        DownloadJob job;
        synchronized (stateLock) {
            job = jobs.get(taskId);
        }
        if (job == null) {
            throw new IllegalArgumentException("下载任务不存在。");
        }
        return job;
    }

    private DownloadState currentState(DownloadTaskId taskId) {
        return get(taskId).map(DownloadTask::state).orElse(DownloadState.CANCELLED);
    }

    private void mutate(DownloadTaskId taskId, UnaryOperator<DownloadTask> operator) {
        synchronized (stateLock) {
            tasks.computeIfPresent(taskId, (id, task) -> operator.apply(task));
        }
    }

    private void updateProgress(DownloadTaskId taskId, DownloadProgress progress) {
        update(taskId, progress.state(), progress, null);
    }

    private void updateFailure(DownloadTaskId taskId, String message) {
        update(taskId, DownloadState.FAILED, null, message);
    }

    private void updateOutputFile(DownloadTaskId taskId, Path outputFile) {
        synchronized (stateLock) {
            tasks.computeIfPresent(taskId, (id, task) -> new DownloadTask(
                    task.id(), task.kind(),
                    outputFile.getFileName() == null ? task.fileName() : outputFile.getFileName().toString(),
                    task.summary(), task.state(), task.progress(), outputFile, task.errorMessage()));
        }
    }

    private void update(DownloadTaskId taskId, DownloadState state, DownloadProgress progress, String error) {
        synchronized (stateLock) {
            tasks.computeIfPresent(taskId, (id, current) -> new DownloadTask(
                    current.id(), current.kind(), current.fileName(), current.summary(), state,
                    progress == null ? current.progress() : progress,
                    current.outputFile(), error));
        }
    }

    private DownloadTask withState(DownloadTask task, DownloadState state) {
        DownloadProgress progress = new DownloadProgress(
                task.progress().percentage(), task.progress().downloadedSize(), task.progress().speed(),
                task.progress().eta(), state);
        return new DownloadTask(task.id(), task.kind(), task.fileName(), task.summary(), state, progress,
                task.outputFile(), task.errorMessage());
    }

    private String userMessage(Exception exception) {
        if (exception instanceof DownloadException) {
            return exception.getMessage();
        }
        return "下载失败，请检查工具路径、网络连接和保存目录权限。";
    }

    @Override
    public void close() {
        handles.values().forEach(DownloadHandle::cancel);
        releaseAllSlots();
        workers.shutdownNow();
    }

    private void releaseAllSlots() {
        synchronized (stateLock) {
            activeDownloads = 0;
            stateLock.notifyAll();
        }
    }
}
