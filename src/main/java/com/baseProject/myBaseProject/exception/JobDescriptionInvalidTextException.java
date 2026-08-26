package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionInvalidTextException extends DomainException {

    public JobDescriptionInvalidTextException(int minTextChars, int maxTextChars) {
        super(
                ErrorCode.JD_INVALID_TEXT,
                HttpStatus.BAD_REQUEST,
                Message.JD_INVALID_TEXT.formatted(minTextChars, maxTextChars));
    }

    public static JobDescriptionInvalidTextException invalidUtf8() {
        return new JobDescriptionInvalidTextException(Message.JD_INVALID_UTF8);
    }

    private JobDescriptionInvalidTextException(String message) {
        super(ErrorCode.JD_INVALID_TEXT, HttpStatus.BAD_REQUEST, message);
    }
}
