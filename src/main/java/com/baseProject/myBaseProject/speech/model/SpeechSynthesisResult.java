package com.baseProject.myBaseProject.speech.model;

public record SpeechSynthesisResult(
        byte[] audio,
        String contentType) {
}
