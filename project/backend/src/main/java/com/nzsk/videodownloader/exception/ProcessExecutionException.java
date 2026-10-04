package com.nzsk.videodownloader.exception;

public class ProcessExecutionException extends Exception {
    public ProcessExecutionException(String message) {
        super(message);
    }

    public ProcessExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
