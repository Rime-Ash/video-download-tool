package com.nzsk.videodownloader.model;

public record EnvironmentStatus(
        boolean javaRuntimeAvailable,
        boolean ytDlpAvailable,
        boolean ffmpegAvailable,
        boolean downloadDirectoryWritable,
        long usableSpace
) {
}
