package com.nzsk.videodownloader.exception;

/**
 * Raised when a Douyin video cannot be resolved or downloaded through the browser fallback.
 */
public class DouyinVideoException extends Exception {
    public DouyinVideoException(String message) {
        super(message);
    }

    public DouyinVideoException(String message, Throwable cause) {
        super(message, cause);
    }
}
