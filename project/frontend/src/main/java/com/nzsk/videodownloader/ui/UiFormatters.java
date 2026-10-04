package com.nzsk.videodownloader.ui;

import com.nzsk.videodownloader.model.DownloadState;

import java.util.Locale;

final class UiFormatters {
    private UiFormatters() {
    }

    static String stateText(DownloadState state) {
        return switch (state) {
            case QUEUED -> "排队中";
            case PARSING -> "解析中";
            case DOWNLOADING -> "下载中";
            case PAUSED -> "已暂停";
            case MERGING -> "合并中";
            case COMPLETED -> "已完成";
            case FAILED -> "失败";
            case CANCELLED -> "已取消";
        };
    }

    static String duration(Long seconds) {
        if (seconds == null || seconds <= 0) {
            return "未知";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainingSeconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
    }

    static String fileSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "未知";
        }
        double megabytes = bytes / 1024.0 / 1024.0;
        if (megabytes < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", megabytes);
        }
        return String.format(Locale.ROOT, "%.2f GB", megabytes / 1024.0);
    }

    static String freeSpace(long bytes) {
        if (bytes <= 0) {
            return "未知";
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    static String unknownIfBlank(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }
}
