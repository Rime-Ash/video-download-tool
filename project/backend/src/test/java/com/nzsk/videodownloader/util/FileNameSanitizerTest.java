package com.nzsk.videodownloader.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNameSanitizerTest {
    @Test
    void removesWindowsInvalidCharactersAndControlCharacters() {
        String result = FileNameSanitizer.sanitize("a\\b:c*?\"<d>|\n");

        assertFalse(result.matches(".*[\\\\/:*?\"<>|].*"));
        assertFalse(result.chars().anyMatch(Character::isISOControl));
    }

    @Test
    void limitsLengthAndProtectsReservedDeviceNames() {
        String result = FileNameSanitizer.sanitize("CON.txt" + "x".repeat(200), 30);

        assertEquals(30, result.length());
        assertTrue(result.startsWith("_CON"));
    }
}
