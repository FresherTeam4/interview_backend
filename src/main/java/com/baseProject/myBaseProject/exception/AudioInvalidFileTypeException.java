package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AudioInvalidFileTypeException extends DomainException {

    public AudioInvalidFileTypeException() {
        super(
                ErrorCode.AUDIO_INVALID_FILE_TYPE,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                Message.AUDIO_INVALID_FILE_TYPE);
    }
}
