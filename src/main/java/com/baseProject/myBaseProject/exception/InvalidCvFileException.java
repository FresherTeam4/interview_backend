package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

/** File CV không hợp lệ: rỗng, không phải PDF, hoặc vượt dung lượng cho phép. */
public class InvalidCvFileException extends DomainException {
    public InvalidCvFileException(String message) {
        super(ErrorCode.INVALID_CV_FILE, HttpStatus.BAD_REQUEST, message);
    }
}
