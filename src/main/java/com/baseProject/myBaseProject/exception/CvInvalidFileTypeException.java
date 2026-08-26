package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Đuôi file không phải {@code .pdf}, hoặc 4 byte đầu không phải {@code %PDF-}.
 *
 * <p>Kiểm cả hai vì {@code Content-Type} do client tự khai, không tin được.
 */
public class CvInvalidFileTypeException extends DomainException {

    public CvInvalidFileTypeException() {
        super(ErrorCode.CV_INVALID_FILE_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                Message.CV_INVALID_FILE_TYPE);
    }
}
