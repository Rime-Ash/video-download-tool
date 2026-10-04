package com.nzsk.videodownloader.ui;

/**
 * Plain entry point used by the packaged application.
 *
 * <p>The JVM refuses to start a main class that extends {@code javafx.application.Application} when the
 * JavaFX modules are on the classpath instead of the module path, which is how {@code jpackage} lays out a
 * non-modular application image. Calling the application from a non-JavaFX class avoids that check while
 * keeping a single JavaFX implementation.</p>
 */
public final class VideoDownloaderLauncher {
    private VideoDownloaderLauncher() {
    }

    public static void main(String[] args) {
        VideoDownloaderApplication.main(args);
    }
}
