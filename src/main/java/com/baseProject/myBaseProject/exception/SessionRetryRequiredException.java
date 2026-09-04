package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class SessionRetryRequiredException extends DomainException {

    public SessionRetryRequiredException() {
        super(
                ErrorCode.SESSION_RETRY_REQUIRED,
                HttpStatus.CONFLICT,
                Message.SESSION_RETRY_REQUIRED);
    }
}
