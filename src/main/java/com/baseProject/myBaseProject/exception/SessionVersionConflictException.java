package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class SessionVersionConflictException extends DomainException {

    public SessionVersionConflictException() {
        super(
                ErrorCode.SESSION_VERSION_CONFLICT,
                HttpStatus.CONFLICT,
                Message.SESSION_VERSION_CONFLICT);
    }
}
