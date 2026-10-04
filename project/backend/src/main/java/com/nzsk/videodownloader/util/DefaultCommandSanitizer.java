package com.nzsk.videodownloader.util;

import java.nio.file.Path;

public final class DefaultCommandSanitizer {
    private DefaultCommandSanitizer() {
    }

    public static String safeOutputTemplate(Path downloadDirectory, String requestedName) {
        String safeName = FileNameSanitizer.sanitize(requestedName);
        return downloadDirectory.toAbsolutePath().normalize().resolve(safeName).normalize().toString();
    }
}
