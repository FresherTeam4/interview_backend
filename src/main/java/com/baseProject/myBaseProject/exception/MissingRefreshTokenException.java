package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;
import org.springframework.http.HttpStatus;

/** Request tới {@code /api/auth/refresh} nhưng không kèm cookie refresh token. */
public class MissingRefreshTokenException extends DomainException {

    public MissingRefreshTokenException() {
        super(ErrorCode.MISSING_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, Message.MISSING_REFRESH_TOKEN);
    }
}
