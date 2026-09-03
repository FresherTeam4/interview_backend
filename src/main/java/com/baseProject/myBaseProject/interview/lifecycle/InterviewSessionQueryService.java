package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewRubricResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.SessionListScope;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.mapper.RubricMapper;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.projection.SessionSummaryProjection;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InterviewSessionQueryService {

    private static final Set<SessionStatus> ACTIVE_STATUSES = Set.copyOf(EnumSet.of(
            SessionStatus.CREATED,
            SessionStatus.SCRIPT_GENERATING,
            SessionStatus.READY,
            SessionStatus.IN_PROGRESS,
            SessionStatus.PAUSED,
            SessionStatus.SCORING,
            SessionStatus.FAILED));
    private static final Set<SessionStatus> HISTORY_STATUSES = Set.of(
            SessionStatus.COMPLETED,
            SessionStatus.ABANDONED);

    private final InterviewSessionRepository sessionRepository;
    private final SessionTurnRepository turnRepository;
    private final InterviewSessionMapper sessionMapper;
    private final RubricMapper rubricMapper;

    @Transactional(readOnly = true)
    public InterviewSessionResponse get(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        return sessionMapper.toResponse(session, turnRepository.findOwnedHistory(sessionId, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InterviewSessionSummaryResponse> list(
            Long userId,
            SessionListScope scope,
            int page,
            int size) {
        Collection<SessionStatus> statuses = statusesFor(scope);
        PageRequest pageable = PageRequest.of(page, size, sortFor(scope));
        Page<SessionSummaryProjection> summaries = sessionRepository
                .findSummariesByUserIdAndStatuses(userId, statuses, pageable);
        return PageResponse.from(summaries.map(sessionMapper::toSummaryResponse));
    }

    @Transactional(readOnly = true)
    public InterviewRubricResponse getRubric(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findOwnedWithLockedRubric(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getTotalQuestionCount() == 0
                || session.getStatus() == SessionStatus.CREATED
                || session.getStatus() == SessionStatus.SCRIPT_GENERATING) {
            throw new SessionInvalidStateException();
        }
        return rubricMapper.toResponse(session.getRubricVersion());
    }

    private Collection<SessionStatus> statusesFor(SessionListScope scope) {
        return switch (scope) {
            case ACTIVE -> ACTIVE_STATUSES;
            case HISTORY -> HISTORY_STATUSES;
            case ALL -> EnumSet.allOf(SessionStatus.class);
        };
    }

    private Sort sortFor(SessionListScope scope) {
        return switch (scope) {
            case ACTIVE, ALL -> Sort.by(
                    Sort.Order.desc("lastActivityAt"),
                    Sort.Order.desc("id"));
            case HISTORY -> Sort.by(
                    Sort.Order.desc("completedAt"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("id"));
        };
    }
}
