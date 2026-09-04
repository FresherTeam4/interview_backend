package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AudioFileTooLargeException extends DomainException {

    public AudioFileTooLargeException(long maxFileSizeBytes) {
        super(
                ErrorCode.AUDIO_FILE_TOO_LARGE,
                HttpStatus.CONTENT_TOO_LARGE,
                Message.AUDIO_FILE_TOO_LARGE.formatted(maxFileSizeBytes / 1024 / 1024));
    }
}
