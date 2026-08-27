package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class SessionRetryNotAllowedException extends DomainException {

    public SessionRetryNotAllowedException() {
        super(
                ErrorCode.SESSION_RETRY_NOT_ALLOWED,
                HttpStatus.CONFLICT,
                Message.SESSION_RETRY_NOT_ALLOWED);
    }
}
