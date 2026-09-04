package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class TranscriptRequiredException extends DomainException {

    public TranscriptRequiredException() {
        super(ErrorCode.TRANSCRIPT_REQUIRED, HttpStatus.BAD_REQUEST, Message.TRANSCRIPT_REQUIRED);
    }
}
