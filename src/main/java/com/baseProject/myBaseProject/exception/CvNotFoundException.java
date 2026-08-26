package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * {@code cvId} không thuộc người gọi, hoặc đã xóa mềm.
 *
 * <p>Cố ý là 404 chứ không phải 403: trả 403 cho CV của người khác là gián tiếp xác nhận
 * id đó tồn tại.
 */
public class CvNotFoundException extends DomainException {

    public CvNotFoundException() {
        super(ErrorCode.CV_NOT_FOUND, HttpStatus.NOT_FOUND, Message.CV_NOT_FOUND);
    }
}
