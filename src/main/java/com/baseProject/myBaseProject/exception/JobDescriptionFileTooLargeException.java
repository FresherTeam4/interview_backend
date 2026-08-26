package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionFileTooLargeException extends DomainException {

    public JobDescriptionFileTooLargeException(long maxFileSizeBytes) {
        super(ErrorCode.JD_FILE_TOO_LARGE, HttpStatus.CONTENT_TOO_LARGE,
                Message.JD_FILE_TOO_LARGE.formatted(maxFileSizeBytes / 1024 / 1024));
    }
}
