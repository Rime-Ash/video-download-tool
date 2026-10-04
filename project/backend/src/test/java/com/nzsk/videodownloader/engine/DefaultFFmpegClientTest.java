package com.nzsk.videodownloader.engine;

import com.nzsk.videodownloader.exception.ProcessExecutionException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultFFmpegClientTest {
    @Test
    void rejectsMissingInputFilesBeforeStartingProcess() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-ffmpeg");
        var client = new DefaultFFmpegClient(directory.resolve("ffmpeg.exe"), command -> {
            throw new AssertionError("process must not start");
        });

        assertThrows(ProcessExecutionException.class, () -> client.merge(
                directory.resolve("video.part"), directory.resolve("audio.part"), directory.resolve("output.mp4")));
    }

    @Test
    void rejectsOutputEqualToInput() throws Exception {
        var directory = Files.createTempDirectory("video-downloader-ffmpeg");
        var video = Files.createFile(directory.resolve("video.part"));
        var audio = Files.createFile(directory.resolve("audio.part"));
        var client = new DefaultFFmpegClient(directory.resolve("ffmpeg.exe"), command -> {
            throw new AssertionError("process must not start");
        });

        assertThrows(ProcessExecutionException.class, () -> client.merge(video, audio, video));
    }
}
