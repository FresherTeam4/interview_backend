package com.baseProject.myBaseProject.speech.model;

public record SpeechSynthesisRequest(
        String text,
        String languageCode) {
}
