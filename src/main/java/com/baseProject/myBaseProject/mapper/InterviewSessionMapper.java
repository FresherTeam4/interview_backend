package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import org.springframework.stereotype.Component;

@Component
public class InterviewSessionMapper {
    public InterviewSessionStatusResponse toStatusResponse(InterviewSession session) {
        return new InterviewSessionStatusResponse(
                session.getId(),
                session.getStatus(),
                session.getTemplateTitleSnapshot(),
                session.getProfileNameSnapshot(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getPreparationErrorCode(),
                session.getPreparationErrorMessage(),
                session.getPreparedAt());
    }
}
