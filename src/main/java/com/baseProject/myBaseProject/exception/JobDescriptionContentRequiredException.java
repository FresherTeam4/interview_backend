package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionContentRequiredException extends DomainException {

    public JobDescriptionContentRequiredException() {
        super(ErrorCode.JD_CONTENT_REQUIRED, HttpStatus.BAD_REQUEST, Message.JD_CONTENT_REQUIRED);
    }
}
