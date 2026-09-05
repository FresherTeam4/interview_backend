package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InterviewContextLoader {
    private final InterviewSessionRepository sessions;
    private final InterviewFocusAreaRepository focusAreas;

    @Transactional(readOnly = true)
    public InterviewContext load(Long sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        // lấy context từ database lên
        return new InterviewContext(
                session.getId(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getJobContextSummary(),
                session.getCandidateContextSummary(),
                session.getOpeningMessage(),
                session.getConversationSummary(),
                session.getCurrentTurnIndex(),
                focusAreas.findBySessionIdOrderByDisplayOrderAsc(sessionId).stream()
                        .map(this::toContext)
                        .toList());
    }

    private InterviewContext.FocusArea toContext(InterviewFocusArea area) {
        return new InterviewContext.FocusArea(
                area.getCode(),
                area.getName(),
                area.getDescription(),
                area.getPriority(),
                area.getReason(),
                area.getPlannedSeconds(),
                area.getEvidenceStatus(),
                area.getEvidenceSummary(),
                area.getDisplayOrder());
    }
}
