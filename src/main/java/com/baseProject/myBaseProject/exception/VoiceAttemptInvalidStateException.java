package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class VoiceAttemptInvalidStateException extends DomainException {

    public VoiceAttemptInvalidStateException() {
        super(
                ErrorCode.VOICE_ATTEMPT_INVALID_STATE,
                HttpStatus.CONFLICT,
                Message.VOICE_ATTEMPT_INVALID_STATE);
    }
}
