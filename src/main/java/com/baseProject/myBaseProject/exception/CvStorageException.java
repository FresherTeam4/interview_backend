package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/** Lỗi đọc/ghi file CV trên storage. */
public class CvStorageException extends DomainException {
    public CvStorageException() {
        super(ErrorCode.CV_STORAGE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR, Message.CV_STORAGE_FAILED);
    }
}
