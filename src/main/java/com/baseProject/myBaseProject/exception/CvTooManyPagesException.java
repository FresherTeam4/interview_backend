package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/** Số trang vượt {@code app.cv.max-pages} — gần như chắc chắn không phải CV. */
public class CvTooManyPagesException extends DomainException {

    public CvTooManyPagesException(int pages, int maxPages) {
        super(ErrorCode.CV_TOO_MANY_PAGES, HttpStatus.BAD_REQUEST,
                Message.CV_TOO_MANY_PAGES.formatted(pages, maxPages));
    }
}
