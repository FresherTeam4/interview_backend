package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionHasNoFileException extends DomainException {

    public JobDescriptionHasNoFileException() {
        super(ErrorCode.JD_HAS_NO_FILE, HttpStatus.CONFLICT, Message.JD_HAS_NO_FILE);
    }
}
