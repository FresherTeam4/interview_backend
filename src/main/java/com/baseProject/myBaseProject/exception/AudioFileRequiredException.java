package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AudioFileRequiredException extends DomainException {

    public AudioFileRequiredException() {
        super(ErrorCode.AUDIO_FILE_REQUIRED, HttpStatus.BAD_REQUEST, Message.AUDIO_FILE_REQUIRED);
    }
}
