package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;


public class GoogleLoginNotConfiguredException extends DomainException {

    public GoogleLoginNotConfiguredException() {
        super(ErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                Message.GOOGLE_LOGIN_NOT_CONFIGURED);
    }
}
