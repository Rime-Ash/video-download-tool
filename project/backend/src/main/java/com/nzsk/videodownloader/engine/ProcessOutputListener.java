package com.nzsk.videodownloader.engine;

@FunctionalInterface
public interface ProcessOutputListener {
    void onLine(StreamType stream, String line);

    default void onComplete(StreamType stream) {
    }

    enum StreamType {
        STDOUT,
        STDERR
    }
}
