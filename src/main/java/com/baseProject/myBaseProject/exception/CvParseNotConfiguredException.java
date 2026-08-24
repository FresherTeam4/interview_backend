package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/** Chưa cấu hình API key cho dịch vụ AI (cả primary lẫn fallback đều dùng dummy key). */
public class CvParseNotConfiguredException extends DomainException {
    public CvParseNotConfiguredException() {
        super(ErrorCode.CV_PARSE_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                Message.CV_PARSE_NOT_CONFIGURED);
    }
}
