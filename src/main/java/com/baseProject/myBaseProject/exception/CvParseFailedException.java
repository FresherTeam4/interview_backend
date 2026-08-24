package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

/** Gọi AI bóc tách CV không thành công (AI lỗi, quá thời gian, hoặc trả về dữ liệu rỗng). */
public class CvParseFailedException extends DomainException {
    public CvParseFailedException(String message) {
        super(ErrorCode.CV_PARSE_FAILED, HttpStatus.BAD_GATEWAY, message);
    }
}
