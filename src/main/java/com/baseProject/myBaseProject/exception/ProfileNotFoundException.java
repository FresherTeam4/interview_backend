package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * {@code profileId} không thuộc người gọi, hoặc CV của nó đã xóa mềm.
 *
 * <p>Hai trường hợp trả cùng một mã, cố ý: một truy vấn
 * {@code findByIdAndUserIdAndCvDocumentActiveTrue} lo cả hai, và người gọi không cần biết
 * mình đang gặp trường hợp nào.
 */
public class ProfileNotFoundException extends DomainException {

    public ProfileNotFoundException() {
        super(ErrorCode.PROFILE_NOT_FOUND, HttpStatus.NOT_FOUND, Message.PROFILE_NOT_FOUND);
    }
}
