package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class AnswerTooLongException extends DomainException {

    public AnswerTooLongException(int maxAnswerChars) {
        super(
                ErrorCode.ANSWER_TOO_LONG,
                HttpStatus.BAD_REQUEST,
                Message.ANSWER_TOO_LONG.formatted(maxAnswerChars));
    }
}
