package com.nzsk.videodownloader.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Central access to the log directory and loggers. Log files never contain cookie values,
 * authentication data or full command lines.
 */
public final class LogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogManager.class);

    private LogManager() {
    }

    public static Path logDirectory() {
        return AppPaths.logDirectory();
    }

    public static void prepareLogDirectory() {
        try {
            Files.createDirectories(logDirectory());
        } catch (IOException exception) {
            LOGGER.warn("无法创建日志目录，日志可能只输出到控制台。");
        }
    }

    public static Logger getLogger(Class<?> type) {
        return LoggerFactory.getLogger(type);
    }
}
