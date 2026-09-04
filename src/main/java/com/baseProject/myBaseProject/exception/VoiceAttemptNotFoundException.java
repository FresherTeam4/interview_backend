package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class VoiceAttemptNotFoundException extends DomainException {

    public VoiceAttemptNotFoundException() {
        super(
                ErrorCode.VOICE_ATTEMPT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                Message.VOICE_ATTEMPT_NOT_FOUND);
    }
}
