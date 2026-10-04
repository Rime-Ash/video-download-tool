package com.nzsk.videodownloader.service;

import com.nzsk.videodownloader.exception.StorageException;

import java.nio.file.Path;

/**
 * Opens finished files or directories with the desktop shell. The UI layer never touches the
 * file system itself.
 */
public interface LocalFileService {
    /** Opens a file or a directory, whichever the path points at. */
    void open(Path path) throws StorageException;

    void openFile(Path file) throws StorageException;

    void openDirectory(Path directory) throws StorageException;
}
