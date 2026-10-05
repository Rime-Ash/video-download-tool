package com.nzsk.videodownloader.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates a Chromium based browser that can render a page in headless mode.
 *
 * <p>Only the executable is located, through the standard installation variables and {@code PATH}. The
 * fallback then launches it with its own throw-away profile directory, so the user's profile, history and
 * cookie database are never opened or copied.</p>
 */
public final class BrowserLocator {
    private static final List<String> RELATIVE_CANDIDATES = List.of(
            "Microsoft/Edge/Application/msedge.exe",
            "Google/Chrome/Application/chrome.exe");
    private static final List<String> EXECUTABLE_NAMES = List.of("msedge.exe", "chrome.exe");
    private static final List<String> INSTALL_VARIABLES = List.of(
            "ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA");

    private BrowserLocator() {
    }

    public static Optional<Path> locate() {
        return locate(candidates());
    }

    /** Injection point used by tests; production passes the paths derived from the environment. */
    public static Optional<Path> locate(List<Path> candidates) {
        if (candidates == null) {
            return Optional.empty();
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst();
    }

    static List<Path> candidates() {
        List<Path> paths = new ArrayList<>();
        for (String variable : INSTALL_VARIABLES) {
            String root = System.getenv(variable);
            if (root == null || root.isBlank()) {
                continue;
            }
            for (String relative : RELATIVE_CANDIDATES) {
                paths.add(Path.of(root, relative.split("/")));
            }
        }
        String searchPath = System.getenv("PATH");
        if (searchPath != null) {
            for (String entry : searchPath.split(File.pathSeparator)) {
                if (entry.isBlank()) {
                    continue;
                }
                for (String name : EXECUTABLE_NAMES) {
                    paths.add(Path.of(entry, name));
                }
            }
        }
        return List.copyOf(paths);
    }
}
