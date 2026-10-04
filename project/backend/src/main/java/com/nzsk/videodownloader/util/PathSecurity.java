package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.StorageException;

import java.nio.file.Path;

public interface PathSecurity {
    /**
     * Sanitizes a requested file name and returns a non-conflicting path inside the download
     * directory.
     */
    Path resolveOutputPath(Path downloadDirectory, String requestedFileName)
            throws StorageException;

    /**
     * Sanitizes a media title and returns a base name (without extension) that does not collide
     * with an existing file of any extension in the download directory.
     */
    Path resolveUniqueBaseName(Path downloadDirectory, String requestedName)
            throws StorageException;
}
