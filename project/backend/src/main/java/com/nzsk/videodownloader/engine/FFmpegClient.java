package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.ProcessExecutionException;

import java.nio.file.Path;

public interface FFmpegClient {
    boolean isAvailable();

    void merge(Path videoFile, Path audioFile, Path outputFile)
            throws ProcessExecutionException;
}
