package com.baseProject.myBaseProject.speech.model;

public record SpeechTranscriptionRequest(
        byte[] audio,
        String filename,
        String contentType,
        String languageCode) {
}
