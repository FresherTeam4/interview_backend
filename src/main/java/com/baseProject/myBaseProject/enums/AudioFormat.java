package com.baseProject.myBaseProject.enums;

public enum AudioFormat {
    WEBM_OPUS("webm", "audio/webm"),
    MP4_AAC("mp4", "audio/mp4");

    private final String extension;
    private final String contentType;

    AudioFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
