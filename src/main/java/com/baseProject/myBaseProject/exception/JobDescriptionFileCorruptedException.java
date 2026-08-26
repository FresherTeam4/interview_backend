package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionFileCorruptedException extends DomainException {

    public JobDescriptionFileCorruptedException() {
        super(ErrorCode.JD_FILE_CORRUPTED, HttpStatus.BAD_REQUEST,
                Message.JD_FILE_CORRUPTED);
    }

    public JobDescriptionFileCorruptedException(Throwable cause) {
        super(ErrorCode.JD_FILE_CORRUPTED, HttpStatus.BAD_REQUEST,
                Message.JD_FILE_CORRUPTED, cause);
    }
}
