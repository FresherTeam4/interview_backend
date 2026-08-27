package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class SessionLimitReachedException extends DomainException {

    public SessionLimitReachedException(int maxActiveSessions) {
        super(
                ErrorCode.SESSION_LIMIT_REACHED,
                HttpStatus.CONFLICT,
                Message.SESSION_LIMIT_REACHED.formatted(maxActiveSessions));
    }
}
