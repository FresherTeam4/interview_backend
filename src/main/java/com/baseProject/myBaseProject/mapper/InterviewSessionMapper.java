package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.session.InterviewFocusAreaResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionResponse;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterviewSessionMapper {
    public InterviewSessionResponse toResponse(
            InterviewSession session, List<InterviewFocusArea> focusAreas) {
        return new InterviewSessionResponse(
                session.getId(),
                session.getVersion(),
                session.getTemplate().getId(),
                session.getTemplateTitleSnapshot(),
                session.getProfile().getId(),
                session.getProfileNameSnapshot(),
                session.getStatus(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getJobContextSummary(),
                session.getCandidateContextSummary(),
                session.getOpeningMessage(),
                session.getPreparationErrorCode(),
                session.getPreparationErrorMessage(),
                session.getPreparationStartedAt(),
                session.getPreparedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                focusAreas.stream().map(this::toFocusArea).toList());
    }

    private InterviewFocusAreaResponse toFocusArea(InterviewFocusArea area) {
        return new InterviewFocusAreaResponse(
                area.getCode(), area.getName(), area.getDescription(), area.getPriority(),
                area.getReason(), area.getPlannedSeconds(), area.getEvidenceStatus(),
                area.getDisplayOrder());
    }
}
