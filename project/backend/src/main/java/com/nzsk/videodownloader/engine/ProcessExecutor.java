package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.ProcessExecutionException;

import java.util.List;

public interface ProcessExecutor {
    Process start(List<String> command) throws ProcessExecutionException;

    default Process start(List<String> command, ProcessOutputListener outputListener)
            throws ProcessExecutionException {
        return start(command);
    }
}
