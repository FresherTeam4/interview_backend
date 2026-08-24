package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/** Mỗi CV chỉ giữ một kết quả bóc tách (uq_cv_parse_results_document). */
public class CvAlreadyParsedException extends DomainException {
    public CvAlreadyParsedException() {
        super(ErrorCode.CV_ALREADY_PARSED, HttpStatus.CONFLICT, Message.CV_ALREADY_PARSED);
    }
}
