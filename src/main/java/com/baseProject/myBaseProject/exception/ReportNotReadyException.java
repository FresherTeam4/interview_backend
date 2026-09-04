package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class ReportNotReadyException extends DomainException {

    public ReportNotReadyException() {
        super(ErrorCode.REPORT_NOT_READY, HttpStatus.CONFLICT, Message.REPORT_NOT_READY);
    }
}
