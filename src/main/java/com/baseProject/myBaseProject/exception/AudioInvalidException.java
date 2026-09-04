package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AudioInvalidException extends DomainException {

    public AudioInvalidException() {
        super(ErrorCode.AUDIO_INVALID, HttpStatus.BAD_REQUEST, Message.AUDIO_INVALID);
    }

    public AudioInvalidException(Throwable cause) {
        super(ErrorCode.AUDIO_INVALID, HttpStatus.BAD_REQUEST, Message.AUDIO_INVALID, cause);
    }
}
