package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class ClientAttemptIdReusedException extends DomainException {

    public ClientAttemptIdReusedException() {
        super(
                ErrorCode.IDEMPOTENCY_KEY_REUSED,
                HttpStatus.CONFLICT,
                Message.CLIENT_ATTEMPT_ID_REUSED);
    }
}
