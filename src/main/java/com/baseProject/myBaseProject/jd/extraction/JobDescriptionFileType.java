package com.baseProject.myBaseProject.jd.extraction;

import com.baseProject.myBaseProject.exception.JobDescriptionInvalidFileTypeException;

import java.util.Locale;

public enum JobDescriptionFileType {
    PDF("pdf", "application/pdf"),
    TXT("txt", "text/plain; charset=UTF-8");

    private final String extension;
    private final String contentType;

    JobDescriptionFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static JobDescriptionFileType fromFilename(String filename) {
        String lowercase = filename.toLowerCase(Locale.ROOT);
        for (JobDescriptionFileType type : values()) {
            if (lowercase.endsWith("." + type.extension)) {
                return type;
            }
        }
        throw new JobDescriptionInvalidFileTypeException();
    }
}
