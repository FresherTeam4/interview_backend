package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;

import org.springframework.stereotype.Component;

@Component
public class InterviewSessionMapper {

    public InterviewSessionAcceptedResponse toAcceptedResponse(InterviewSession session) {
        return new InterviewSessionAcceptedResponse(
                session.getId(),
                session.getStatus(),
                session.getAwaitingAction(),
                session.getVersion(),
                session.getCreatedAt());
    }

    public InterviewSessionResponse toResponse(InterviewSession session) {
        return new InterviewSessionResponse(
                session.getId(),
                new InterviewSessionResponse.ProfileReference(
                        session.getProfile().getId(),
                        session.getProfile().getHeadline()),
                new InterviewSessionResponse.JobDescriptionReference(
                        session.getJobDescription().getId(),
                        session.getJobDescription().getTitle()),
                session.getDifficulty(),
                session.getMode(),
                session.getLanguageCode(),
                session.getStatus(),
                session.getAwaitingAction(),
                session.getVersion(),
                session.getAnsweredQuestionCount(),
                session.getTotalQuestionCount(),
                session.getStatusMessage(),
                session.getLastActivityAt(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
