package com.nzsk.videodownloader.exception;

public class UrlValidationException extends Exception {
    public UrlValidationException(String message) {
        super(message);
    }

    public UrlValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
