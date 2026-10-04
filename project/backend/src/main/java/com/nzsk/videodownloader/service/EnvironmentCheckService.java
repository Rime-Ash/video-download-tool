package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.AppConfig;
import com.nzsk.videodownloader.model.EnvironmentStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class EnvironmentCheckService {
    private final int requiredJavaFeatureVersion;

    public EnvironmentCheckService() {
        this(21);
    }

    public EnvironmentCheckService(int requiredJavaFeatureVersion) {
        if (requiredJavaFeatureVersion < 1) {
            throw new IllegalArgumentException("requiredJavaFeatureVersion must be positive");
        }
        this.requiredJavaFeatureVersion = requiredJavaFeatureVersion;
    }

    public EnvironmentStatus check(AppConfig config) {
        Objects.requireNonNull(config, "config");
        Path downloadDirectory = config.downloadDirectory().toAbsolutePath().normalize();
        boolean writable = canWrite(downloadDirectory);
        long usableSpace = Files.exists(downloadDirectory)
                ? downloadDirectory.toFile().getUsableSpace()
                : downloadDirectory.getParent() == null
                ? 0L
                : downloadDirectory.getParent().toFile().getUsableSpace();
        return new EnvironmentStatus(
                Runtime.version().feature() >= requiredJavaFeatureVersion,
                isRegularFile(config.ytDlpPath()),
                isRegularFile(config.ffmpegPath()),
                writable,
                usableSpace
        );
    }

    private boolean canWrite(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.isDirectory(directory) && Files.isWritable(directory);
        } catch (java.io.IOException exception) {
            return false;
        }
    }

    private boolean isRegularFile(Path path) {
        return path != null && Files.isRegularFile(path) && Files.isReadable(path);
    }
}
