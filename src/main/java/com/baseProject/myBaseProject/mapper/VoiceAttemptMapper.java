package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;

import org.springframework.stereotype.Component;

@Component
public class VoiceAttemptMapper {

    public VoiceAttemptResponse toResponse(VoiceAnswerAttempt attempt) {
        return new VoiceAttemptResponse(
                attempt.getId(),
                attempt.getPromptTurn().getId(),
                attempt.getAttemptNo(),
                attempt.getStatus(),
                attempt.getVersion(),
                attempt.getRawText(),
                attempt.getEditedText(),
                attempt.getDurationMs(),
                attempt.getStatusMessage(),
                attempt.getCreatedAt());
    }
}
