package com.baseProject.myBaseProject.speech;

import com.baseProject.myBaseProject.speech.model.SpeechSynthesisRequest;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;

public interface TextToSpeechProvider {
    String name();

    // Danh tính cache phải phản ánh mọi cấu hình có thể làm thay đổi audio đầu ra.
    String cacheIdentity(String languageCode);

    String outputContentType();

    SpeechSynthesisResult synthesize(SpeechSynthesisRequest request);
}
