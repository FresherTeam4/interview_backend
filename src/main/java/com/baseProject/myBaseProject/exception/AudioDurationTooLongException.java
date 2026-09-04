package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AudioDurationTooLongException extends DomainException {

    public AudioDurationTooLongException(int maxDurationMs) {
        super(
                ErrorCode.AUDIO_DURATION_TOO_LONG,
                HttpStatus.CONTENT_TOO_LARGE,
                Message.AUDIO_DURATION_TOO_LONG.formatted(maxDurationMs / 1000));
    }
}
