package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends DomainException {

    public SessionNotFoundException() {
        super(ErrorCode.SESSION_NOT_FOUND, HttpStatus.NOT_FOUND, Message.SESSION_NOT_FOUND);
    }
}
