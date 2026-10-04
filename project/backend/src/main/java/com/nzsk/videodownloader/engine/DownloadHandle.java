package com.nzsk.videodownloader.engine;

import java.nio.file.Path;
import java.util.Optional;

public interface DownloadHandle {
    void pause();

    void cancel();

    void awaitCompletion() throws InterruptedException;

    boolean succeeded();

    default Optional<Path> outputFile() {
        return Optional.empty();
    }

    /** Reason reported to the user when the job failed, when the implementation knows one. */
    default Optional<String> failureMessage() {
        return Optional.empty();
    }
}
