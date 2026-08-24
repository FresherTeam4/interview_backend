package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

/**
 * Dữ liệu hồ sơ người dùng sửa vi phạm ràng buộc nghiệp vụ mà annotation không kiểm tra được
 * (ví dụ năm kết thúc nhỏ hơn năm bắt đầu - trùng với CHECK trong changeset 022).
 */
public class InvalidProfileDataException extends DomainException {
    public InvalidProfileDataException(String message) {
        super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }
}
