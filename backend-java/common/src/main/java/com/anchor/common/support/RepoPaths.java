package com.anchor.common.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RepoPaths {

    private RepoPaths() {
    }

    public static Path findUp(String relative) {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve(relative).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    public static Path resolveConfigured(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("path is not configured");
        }
        Path given = Path.of(configured);
        if (given.isAbsolute() && Files.exists(given)) {
            return given;
        }
        Path fromCwd = Path.of("").toAbsolutePath().resolve(given).normalize();
        if (Files.exists(fromCwd)) {
            return fromCwd;
        }
        String fileName = given.getFileName() == null ? configured : given.getFileName().toString();
        Path walked = findUp(relativeTail(given));
        if (walked != null) {
            return walked;
        }
        return fromCwd.resolve(fileName);
    }

    private static String relativeTail(Path given) {
        if (given.getNameCount() >= 2) {
            return given.subpath(given.getNameCount() - 2, given.getNameCount()).toString();
        }
        return given.toString();
    }

    public static String flywayLocation(Path migrationsDir) {
        try {
            return "filesystem:" + migrationsDir.toRealPath();
        } catch (IOException ex) {
            return "filesystem:" + migrationsDir.toAbsolutePath();
        }
    }
}
