package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.ProcessExecutionException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class DefaultFFmpegClient implements FFmpegClient {
    private final Path executable;
    private final ProcessExecutor processExecutor;

    public DefaultFFmpegClient(Path executable, ProcessExecutor processExecutor) {
        this.executable = Objects.requireNonNull(executable, "executable");
        this.processExecutor = Objects.requireNonNull(processExecutor, "processExecutor");
    }

    @Override
    public boolean isAvailable() {
        return Files.isRegularFile(executable) && Files.isReadable(executable);
    }

    @Override
    public void merge(Path videoFile, Path audioFile, Path outputFile)
            throws ProcessExecutionException {
        Path video = requireReadableFile(videoFile, "视频流文件");
        Path audio = requireReadableFile(audioFile, "音频流文件");
        Path output = Objects.requireNonNull(outputFile, "outputFile")
                .toAbsolutePath().normalize();
        if (video.equals(output) || audio.equals(output)) {
            throw new ProcessExecutionException("合并输出文件不能覆盖输入文件。");
        }
        Path parent = output.getParent();
        if (parent == null) {
            throw new ProcessExecutionException("合并输出路径无效。");
        }
        try {
            Files.createDirectories(parent);
        } catch (java.io.IOException exception) {
            throw new ProcessExecutionException("无法创建媒体输出目录。", exception);
        }
        List<String> command = List.of(
                executable.toString(), "-hide_banner", "-loglevel", "error",
                "-i", video.toString(), "-i", audio.toString(),
                "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-y", output.toString()
        );
        Process process = processExecutor.start(command);
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new ProcessExecutionException("音视频合并超时。");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ProcessExecutionException("音视频合并被中断。", exception);
        }
        if (process.exitValue() != 0) {
            throw new ProcessExecutionException("音视频合并失败，请确认 FFmpeg 可用且输入文件完整。");
        }
    }

    private Path requireReadableFile(Path file, String label) throws ProcessExecutionException {
        Path normalized = Objects.requireNonNull(file, label).toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new ProcessExecutionException(label + "不存在或不可读。");
        }
        return normalized;
    }
}
