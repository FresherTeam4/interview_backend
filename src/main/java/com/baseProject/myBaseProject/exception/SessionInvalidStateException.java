package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class SessionInvalidStateException extends DomainException {

    public SessionInvalidStateException() {
        super(
                ErrorCode.SESSION_INVALID_STATE,
                HttpStatus.CONFLICT,
                Message.SESSION_INVALID_STATE);
    }
}
