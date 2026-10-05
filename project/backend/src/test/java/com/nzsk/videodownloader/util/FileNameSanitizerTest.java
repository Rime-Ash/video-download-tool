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

    /** A lone surrogate half cannot be written as a Windows file name, so the emoji is dropped whole. */
    @Test
    void doesNotSplitSurrogatePairsWhenTruncating() {
        String result = FileNameSanitizer.sanitize("a".repeat(9) + "\uD83D\uDE00", 10);

        assertEquals("a".repeat(9), result);
        assertFalse(result.chars().anyMatch(value -> Character.isSurrogate((char) value)));
    }

    @Test
    void fallsBackToADefaultNameWhenTruncationLeavesNothing() {
        assertEquals("download", FileNameSanitizer.sanitize("\uD83D\uDE00abc", 1));
    }
}
