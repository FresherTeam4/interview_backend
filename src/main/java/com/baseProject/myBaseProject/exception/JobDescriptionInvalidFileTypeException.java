package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionInvalidFileTypeException extends DomainException {

    public JobDescriptionInvalidFileTypeException() {
        super(ErrorCode.JD_INVALID_FILE_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                Message.JD_INVALID_FILE_TYPE);
    }
}
