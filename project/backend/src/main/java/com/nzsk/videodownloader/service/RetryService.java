package com.nzsk.videodownloader.service;

import java.util.Objects;
import java.util.function.LongConsumer;

public final class RetryService {
    private final int maxAttempts;
    private final LongConsumer sleeper;

    public RetryService(int maxAttempts, LongConsumer sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public <T> T execute(Attempt<T> attempt) throws Exception {
        Exception last = null;
        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            try {
                return attempt.run(attemptNumber);
            } catch (Exception exception) {
                last = exception;
                if (attemptNumber < maxAttempts) {
                    long delayMillis = Math.min(30_000L, 1_000L * (1L << (attemptNumber - 1)));
                    sleeper.accept(delayMillis);
                }
            }
        }
        throw last;
    }

    @FunctionalInterface
    public interface Attempt<T> {
        T run(int attemptNumber) throws Exception;
    }
}
