package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends DomainException {

    public IdempotencyKeyReusedException() {
        super(
                ErrorCode.IDEMPOTENCY_KEY_REUSED,
                HttpStatus.CONFLICT,
                Message.IDEMPOTENCY_KEY_REUSED);
    }
}
