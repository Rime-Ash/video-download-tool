package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.exception.UrlValidationException;
import com.nzsk.videodownloader.model.EnvironmentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppContextTest {
    @Test
    void wiresServicesAndReportsEnvironment() {
        try (AppContext context = new AppContext()) {
            assertNotNull(context.config().downloadDirectory());
            assertNotNull(context.downloadOptions());
            assertNotNull(context.pathSecurity());
            assertNotNull(context.localFileService());

            EnvironmentStatus status = context.environmentStatus();
            assertTrue(status.javaRuntimeAvailable());
        }
    }

    @Test
    void rejectsDangerousSchemesBeforeAnyNetworkCall() {
        try (AppContext context = new AppContext()) {
            assertThrows(UrlValidationException.class,
                    () -> context.validateUrl("file:///C:/secret/video.mp4"));
            assertThrows(UrlValidationException.class,
                    () -> context.validateUrl("https://example.com/video"));
        }
    }
}
