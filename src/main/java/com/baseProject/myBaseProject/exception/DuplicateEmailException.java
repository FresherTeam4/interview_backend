package com.baseProject.myBaseProject.exception;

import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends DomainException {

    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL, HttpStatus.CONFLICT, "Email already registered");
    }
}
