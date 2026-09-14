package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionTransitionResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionTransitionRepository;
import com.baseProject.myBaseProject.service.AdminInterviewSessionService;
import com.baseProject.myBaseProject.service.InterviewReportService;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminInterviewSessionServiceImpl implements AdminInterviewSessionService {
    private final InterviewSessionRepository sessions;
    private final InterviewSessionTransitionRepository transitions;
    private final InterviewSessionService interviewSessionService;
    private final InterviewReportService interviewReportService;

    @Override
    @Transactional(readOnly = true)
    public AdminPageResponse<AdminSessionSummaryResponse> list(
            String keyword,
            InterviewSessionStatus status,
            InterviewSessionMode mode,
            Instant createdFrom,
            Instant createdTo,
            int page,
            int size) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "createdFrom must be before or equal to createdTo");
        }
        Page<InterviewSession> result = sessions.searchForAdmin(
                normalizeKeyword(keyword),
                status,
                mode,
                createdFrom,
                createdTo,
                pageRequest(page, size));
        return new AdminPageResponse<>(
                result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSessionDetailResponse get(Long sessionId) {
        InterviewSession session = sessions.findByIdForAdmin(sessionId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        List<AdminSessionTransitionResponse> history = transitions
                .findBySessionIdOrderByOccurredAtAsc(sessionId)
                .stream()
                .map(this::toTransition)
                .toList();
        return toDetail(session, history);
    }

    @Override
    public AdminSessionDetailResponse retryPreparation(Long adminId, Long sessionId) {
        interviewSessionService.retryPreparationForAdmin(sessionId);
        log.info("Admin {} retried interview preparation for session {}", adminId, sessionId);
        return get(sessionId);
    }

    @Override
    public AdminSessionDetailResponse retryScoring(Long adminId, Long sessionId) {
        interviewReportService.retryScoringForAdmin(sessionId);
        log.info("Admin {} retried interview scoring for session {}", adminId, sessionId);
        return get(sessionId);
    }

    private AdminSessionSummaryResponse toSummary(InterviewSession session) {
        return new AdminSessionSummaryResponse(
                session.getId(),
                toUser(session.getUser()),
                session.getStatus(),
                session.getTemplateTitleSnapshot(),
                session.getProfileNameSnapshot(),
                session.getMode(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getPreparationErrorCode(),
                session.getScoringErrorCode(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    private AdminSessionDetailResponse toDetail(
            InterviewSession session,
            List<AdminSessionTransitionResponse> history) {
        return new AdminSessionDetailResponse(
                session.getId(),
                toUser(session.getUser()),
                session.getStatus(),
                session.getTemplateTitleSnapshot(),
                session.getProfileNameSnapshot(),
                session.getLanguageCode(),
                session.getDurationMinutes(),
                session.getInterviewerStyle(),
                session.getMode(),
                session.getRealtimeProvider(),
                session.getRealtimeVoiceName(),
                session.getCurrentTurnIndex(),
                session.getPreparationErrorCode(),
                session.getPreparationErrorMessage(),
                session.getPlanSchemaVersion(),
                session.getPlanModelName(),
                session.getPlanPromptVersion(),
                session.getScoringErrorCode(),
                session.getScoringErrorMessage(),
                session.getPreparationStartedAt(),
                session.getPreparedAt(),
                session.getStartedAt(),
                session.getDeadlineAt(),
                session.getLastActivityAt(),
                session.getEndReason(),
                session.getEndedAt(),
                session.getScoringStartedAt(),
                session.getCompletedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                history);
    }

    private AdminSessionTransitionResponse toTransition(
            InterviewSessionTransition transition) {
        return new AdminSessionTransitionResponse(
                transition.getId(),
                transition.getFromStatus(),
                transition.getToStatus(),
                transition.getReason(),
                transition.getActor(),
                transition.getOccurredAt());
    }

    private AdminUserReferenceResponse toUser(UserAccount user) {
        return new AdminUserReferenceResponse(
                user.getId(), user.getFullName(), user.getEmail());
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
}
