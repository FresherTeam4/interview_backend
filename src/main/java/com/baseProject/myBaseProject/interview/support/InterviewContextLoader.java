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
    public InterviewContext loadOwned(Long userId, Long sessionId) {
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        return toContext(session);
    }

    @Transactional(readOnly = true)
    public InterviewContext loadInternal(Long sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        return toContext(session);
    }

    private InterviewContext toContext(InterviewSession session) {
        return new InterviewContext(
                session.getId(),
                session.getStatus(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getJobContextSummary(),
                session.getCandidateContextSummary(),
                session.getOpeningMessage(),
                session.getConversationSummary(),
                session.getStartedAt(),
                session.getDeadlineAt(),
                session.getCurrentTurnIndex(),
                focusAreas.findBySessionIdOrderByDisplayOrderAsc(session.getId()).stream()
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
