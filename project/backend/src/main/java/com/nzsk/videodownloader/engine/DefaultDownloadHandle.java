package com.nzsk.videodownloader.engine;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class DefaultDownloadHandle implements DownloadHandle {
    private final Process process;
    private final Supplier<Path> outputFileSupplier;

    public DefaultDownloadHandle(Process process) {
        this(process, () -> null);
    }

    public DefaultDownloadHandle(Process process, Supplier<Path> outputFileSupplier) {
        this.process = Objects.requireNonNull(process, "process");
        this.outputFileSupplier = Objects.requireNonNull(outputFileSupplier, "outputFileSupplier");
    }

    @Override
    public void pause() {
        terminate(false);
    }

    @Override
    public void cancel() {
        terminate(true);
    }

    @Override
    public void awaitCompletion() throws InterruptedException {
        process.waitFor();
    }

    @Override
    public boolean succeeded() {
        return !process.isAlive() && process.exitValue() == 0;
    }

    @Override
    public Optional<Path> outputFile() {
        return Optional.ofNullable(outputFileSupplier.get());
    }

    private void terminate(boolean force) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        if (force) {
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
