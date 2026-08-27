package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class ClientTurnIdReusedException extends DomainException {

    public ClientTurnIdReusedException() {
        super(
                ErrorCode.IDEMPOTENCY_KEY_REUSED,
                HttpStatus.CONFLICT,
                Message.CLIENT_TURN_ID_REUSED);
    }
}
