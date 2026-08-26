package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class JobDescriptionLimitReachedException extends DomainException {

    public JobDescriptionLimitReachedException(int maxPerUser) {
        super(
                ErrorCode.JD_LIMIT_REACHED,
                HttpStatus.CONFLICT,
                Message.JD_LIMIT_REACHED.formatted(maxPerUser));
    }
}
