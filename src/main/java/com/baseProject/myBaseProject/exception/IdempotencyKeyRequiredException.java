package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyRequiredException extends DomainException {

    public IdempotencyKeyRequiredException() {
        super(
                ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                HttpStatus.BAD_REQUEST,
                Message.IDEMPOTENCY_KEY_REQUIRED);
    }
}
