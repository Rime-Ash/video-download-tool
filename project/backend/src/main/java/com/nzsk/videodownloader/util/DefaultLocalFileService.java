package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.StorageException;
import com.nzsk.videodownloader.service.LocalFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class DefaultLocalFileService implements LocalFileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLocalFileService.class);

    @Override
    public void open(Path path) throws StorageException {
        Path target = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        if (Files.isDirectory(target)) {
            openDirectory(target);
            return;
        }
        openFile(target);
    }

    @Override
    public void openFile(Path file) throws StorageException {
        Path target = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (!Files.isRegularFile(target)) {
            throw new StorageException("文件不存在或已被移动。");
        }
        open(desktop -> desktop.open(target.toFile()), "无法打开该文件，请检查系统默认程序设置。");
    }

    @Override
    public void openDirectory(Path directory) throws StorageException {
        Path target = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        if (!Files.isDirectory(target)) {
            throw new StorageException("目录不存在，请在设置中重新选择下载目录。");
        }
        open(desktop -> desktop.open(target.toFile()), "无法打开该目录，请检查系统设置。");
    }

    private void open(DesktopAction action, String failureMessage) throws StorageException {
        if (!Desktop.isDesktopSupported()) {
            throw new StorageException("当前系统不支持打开文件或目录。");
        }
        try {
            action.run(Desktop.getDesktop());
        } catch (IOException | UnsupportedOperationException exception) {
            LOGGER.warn("打开本地资源失败：{}", exception.getClass().getSimpleName());
            throw new StorageException(failureMessage, exception);
        }
    }

    @FunctionalInterface
    private interface DesktopAction {
        void run(Desktop desktop) throws IOException;
    }
}
