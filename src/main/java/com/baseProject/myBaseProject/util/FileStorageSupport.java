package com.baseProject.myBaseProject.util;

import java.time.Duration;

public final class FileStorageSupport {
    public static final String PDF_CONTENT_TYPE = "application/pdf";
    public static final Duration PRESIGNED_URL_TTL = Duration.ofMinutes(5);

    private static final int MAX_FILENAME_LENGTH = 255;

    private FileStorageSupport() {
    }

    public static String sanitizeFilename(String rawFilename, String fallback) {
        if (rawFilename == null) {
            return fallback;
        }
        String filename = rawFilename.strip();
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (separator >= 0) {
            filename = filename.substring(separator + 1).strip();
        }
        if (filename.isBlank()) {
            return fallback;
        }
        return filename.length() <= MAX_FILENAME_LENGTH
                ? filename : filename.substring(0, MAX_FILENAME_LENGTH);
    }
}
