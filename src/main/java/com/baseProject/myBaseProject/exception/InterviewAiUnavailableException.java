package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

public class InterviewAiUnavailableException extends DomainException {

    public InterviewAiUnavailableException() {
        super(
                ErrorCode.INTERVIEW_AI_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                Message.INTERVIEW_AI_UNAVAILABLE);
    }
}
