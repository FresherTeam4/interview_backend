package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionTooManyPagesException extends DomainException {

    public JobDescriptionTooManyPagesException(int pages, int maxPages) {
        super(ErrorCode.JD_TOO_MANY_PAGES, HttpStatus.BAD_REQUEST,
                Message.JD_TOO_MANY_PAGES.formatted(pages, maxPages));
    }
}
