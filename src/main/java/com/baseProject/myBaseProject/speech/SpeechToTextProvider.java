package com.baseProject.myBaseProject.speech;

import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionRequest;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;

public interface SpeechToTextProvider {
    String name();

    SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request);
}
