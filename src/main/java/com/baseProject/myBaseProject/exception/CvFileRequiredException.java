package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/** Không có field {@code file} trong multipart, hoặc file rỗng 0 byte. */
public class CvFileRequiredException extends DomainException {

    public CvFileRequiredException() {
        super(ErrorCode.CV_FILE_REQUIRED, HttpStatus.BAD_REQUEST, Message.CV_FILE_REQUIRED);
    }
}
