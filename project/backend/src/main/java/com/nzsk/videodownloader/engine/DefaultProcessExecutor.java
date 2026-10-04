package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.ProcessExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DefaultProcessExecutor implements ProcessExecutor, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProcessExecutor.class);

    private final ExecutorService streamReaders = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "video-downloader-process-reader");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public Process start(List<String> command) throws ProcessExecutionException {
        return start(command, (stream, line) -> {
            // Drain output by default without logging potentially sensitive content.
        });
    }

    @Override
    public Process start(List<String> command, ProcessOutputListener outputListener)
            throws ProcessExecutionException {
        validateCommand(command);
        Objects.requireNonNull(outputListener, "outputListener");
        try {
            Process process = new ProcessBuilder(List.copyOf(command)).start();
            streamReaders.submit(() -> drain(process.getInputStream(), ProcessOutputListener.StreamType.STDOUT, outputListener));
            streamReaders.submit(() -> drain(process.getErrorStream(), ProcessOutputListener.StreamType.STDERR, outputListener));
            return process;
        } catch (IOException exception) {
            throw new ProcessExecutionException("无法启动外部工具，请检查程序路径和文件权限。", exception);
        }
    }

    private void validateCommand(List<String> command) throws ProcessExecutionException {
        if (command == null || command.isEmpty()) {
            throw new ProcessExecutionException("外部命令不能为空。");
        }
        if (command.stream().anyMatch(Objects::isNull)) {
            throw new ProcessExecutionException("外部命令包含无效参数。");
        }
        if (command.stream().anyMatch(this::isShellWrapperArgument)) {
            throw new ProcessExecutionException("不允许通过 Shell 包装器执行外部命令。");
        }
    }

    private boolean isShellWrapperArgument(String argument) {
        String normalized = argument.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("cmd")
                || normalized.equals("cmd.exe")
                || normalized.equals("powershell")
                || normalized.equals("powershell.exe")
                || normalized.equals("pwsh")
                || normalized.equals("pwsh.exe")
                || normalized.equals("-command")
                || normalized.equals("-c")
                || normalized.equals("/c");
    }

    private void drain(InputStream stream, ProcessOutputListener.StreamType streamType,
                       ProcessOutputListener outputListener) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputListener.onLine(streamType, line);
            }
        } catch (IOException exception) {
            LOGGER.debug("外部进程输出读取结束。");
        } finally {
            outputListener.onComplete(streamType);
        }
    }

    @Override
    public void close() {
        streamReaders.shutdownNow();
    }
}
