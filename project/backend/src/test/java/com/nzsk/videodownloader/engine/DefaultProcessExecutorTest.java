package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.ProcessExecutionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultProcessExecutorTest {
    @Test
    void rejectsShellWrappers() {
        try (var executor = new DefaultProcessExecutor()) {
            assertThrows(ProcessExecutionException.class,
                    () -> executor.start(List.of("cmd", "/c", "whoami")));
        }
    }

    @Test
    void rejectsEmptyCommands() {
        try (var executor = new DefaultProcessExecutor()) {
            assertThrows(ProcessExecutionException.class, () -> executor.start(List.of()));
        }
    }
}
