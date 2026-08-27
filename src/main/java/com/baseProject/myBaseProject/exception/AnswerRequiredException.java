package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AnswerRequiredException extends DomainException {

    public AnswerRequiredException() {
        super(ErrorCode.ANSWER_REQUIRED, HttpStatus.BAD_REQUEST, Message.ANSWER_REQUIRED);
    }
}
