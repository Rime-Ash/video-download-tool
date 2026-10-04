package com.nzsk.videodownloader.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessOutputListenerTest {
    @Test
    void rejectsNullOutputListener() {
        try (var executor = new DefaultProcessExecutor()) {
            assertThrows(NullPointerException.class,
                    () -> executor.start(List.of("java"), null));
        }
    }
}
