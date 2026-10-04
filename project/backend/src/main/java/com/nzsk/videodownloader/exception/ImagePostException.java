package com.nzsk.videodownloader.exception;

/**
 * Raised when a Douyin image post (图文/图集) cannot be inspected or downloaded.
 */
public class ImagePostException extends Exception {
    public ImagePostException(String message) {
        super(message);
    }

    public ImagePostException(String message, Throwable cause) {
        super(message, cause);
    }
}
