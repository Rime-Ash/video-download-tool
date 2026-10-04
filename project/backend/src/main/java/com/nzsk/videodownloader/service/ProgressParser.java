package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.model.DownloadProgress;
import com.nzsk.videodownloader.model.DownloadState;

import java.util.Optional;

public final class ProgressParser {
    private ProgressParser() {
    }

    public static Optional<DownloadProgress> parse(String line) {
        if (line == null || !line.startsWith("download:")) {
            return Optional.empty();
        }
        String[] fields = line.substring("download:".length()).split("\\|", -1);
        if (fields.length != 4) {
            return Optional.empty();
        }
        try {
            double percentage = Double.parseDouble(fields[0].replace("%", "").trim());
            return Optional.of(new DownloadProgress(
                    Math.max(0, Math.min(100, percentage)),
                    fields[1],
                    fields[2],
                    fields[3],
                    DownloadState.DOWNLOADING
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
