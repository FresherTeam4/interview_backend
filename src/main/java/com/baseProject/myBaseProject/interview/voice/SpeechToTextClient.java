package com.baseProject.myBaseProject.interview.voice;

import java.math.BigDecimal;

public interface SpeechToTextClient {

    TranscriptionOutcome transcribe(TranscriptionInput input);

    record TranscriptionInput(
            byte[] audio,
            String contentType,
            String languageCode) {
    }

    record TranscriptionOutcome(
            String rawText,
            String provider,
            BigDecimal confidence) {
    }
}
