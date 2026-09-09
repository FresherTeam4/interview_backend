package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import org.springframework.web.multipart.MultipartFile;

public interface SpeechService {
    SpeechTranscriptionResult transcribe(
            Long userId, Long sessionId, MultipartFile audio);

    SpeechSynthesisResult synthesizeInterviewerTurn(
            Long userId, Long sessionId, Long turnId);
}
