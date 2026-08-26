package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Bóc tách lại một CV mà {@code status} không phải {@code FAILED}.
 *
 * <p>Đã {@code PARSED} thì bóc tách lại là đốt tiền API vô ích, mà hồ sơ cũ có thể đã được
 * sửa tay. Muốn dựng lại hồ sơ từ prompt mới là một endpoint khác, chưa làm.
 */
public class CvParseNotRetryableException extends DomainException {

    public CvParseNotRetryableException() {
        super(ErrorCode.CV_PARSE_NOT_RETRYABLE, HttpStatus.CONFLICT,
                Message.CV_PARSE_NOT_RETRYABLE);
    }
}
