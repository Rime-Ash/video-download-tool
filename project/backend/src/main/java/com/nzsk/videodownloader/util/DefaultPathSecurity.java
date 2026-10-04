package com.nzsk.videodownloader.util;

import com.nzsk.videodownloader.exception.StorageException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class DefaultPathSecurity implements PathSecurity {
    private static final int MAX_BASE_NAME_LENGTH = 120;

    @Override
    public Path resolveOutputPath(Path downloadDirectory, String requestedFileName)
            throws StorageException {
        Path base = requireWritableDirectory(downloadDirectory);
        String safeName = FileNameSanitizer.sanitize(requestedFileName);
        Path target = base.resolve(safeName).normalize();
        if (!target.startsWith(base)) {
            throw new StorageException("保存文件路径无效，已阻止目录穿越。");
        }
        return uniquePath(target);
    }

    @Override
    public Path resolveUniqueBaseName(Path downloadDirectory, String requestedName)
            throws StorageException {
        Path base = requireWritableDirectory(downloadDirectory);
        String safeName = FileNameSanitizer.sanitize(FileNameSanitizer.stripMediaExtension(requestedName),
                MAX_BASE_NAME_LENGTH);
        String candidate = safeName;
        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            if (!baseNameExists(base, candidate)) {
                Path target = base.resolve(candidate).normalize();
                if (!target.startsWith(base)) {
                    throw new StorageException("保存文件路径无效，已阻止目录穿越。");
                }
                return target;
            }
            candidate = safeName + " (" + index + ")";
        }
        throw new StorageException("无法生成不冲突的输出文件名。");
    }

    private Path requireWritableDirectory(Path downloadDirectory) throws StorageException {
        Objects.requireNonNull(downloadDirectory, "downloadDirectory");
        Path base = downloadDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
            if (!Files.isDirectory(base) || !Files.isWritable(base)) {
                throw new StorageException("下载目录不可写，请在设置中选择其他目录。");
            }
        } catch (java.io.IOException exception) {
            throw new StorageException("无法访问下载目录，请检查目录权限。", exception);
        }
        return base;
    }

    private boolean baseNameExists(Path directory, String baseName) throws StorageException {
        String expected = baseName.toLowerCase(Locale.ROOT);
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .map(DefaultPathSecurity::withoutExtension)
                    .anyMatch(name -> name.toLowerCase(Locale.ROOT).equals(expected));
        } catch (java.io.IOException exception) {
            throw new StorageException("无法读取下载目录内容，请检查目录权限。", exception);
        }
    }

    private static String withoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private Path uniquePath(Path target) throws StorageException {
        if (!Files.exists(target)) {
            return target;
        }
        String fileName = target.getFileName().toString();
        String base = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }
        for (int index = 1; index < Integer.MAX_VALUE; index++) {
            Path candidate = target.resolveSibling(base + " (" + index + ")" + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new StorageException("无法生成不冲突的输出文件名。");
    }
}
