package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class CurrentPromptMismatchException extends DomainException {

    public CurrentPromptMismatchException() {
        super(
                ErrorCode.CURRENT_PROMPT_MISMATCH,
                HttpStatus.CONFLICT,
                Message.CURRENT_PROMPT_MISMATCH);
    }
}
