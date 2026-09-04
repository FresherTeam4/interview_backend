package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class VoiceAttemptVersionConflictException extends DomainException {

    public VoiceAttemptVersionConflictException() {
        super(
                ErrorCode.VOICE_ATTEMPT_VERSION_CONFLICT,
                HttpStatus.CONFLICT,
                Message.VOICE_ATTEMPT_VERSION_CONFLICT);
    }
}
