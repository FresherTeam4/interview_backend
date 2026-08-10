package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

/**
 * Refresh token không tồn tại, hết hạn, hoặc đã bị thu hồi
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN, HttpStatus.UNAUTHORIZED, "Refresh token is invalid or expired");
    }
}
