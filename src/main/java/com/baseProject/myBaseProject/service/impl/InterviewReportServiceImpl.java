package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.session.InterviewReportResponse;
import com.baseProject.myBaseProject.entity.InterviewAssessment;
import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewScoringRequestedEvent;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewAssessmentRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaResultRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.service.InterviewReportService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class InterviewReportServiceImpl implements InterviewReportService {
    private final InterviewSessionRepository sessions;
    private final InterviewAssessmentRepository assessments;
    private final InterviewFocusAreaResultRepository focusAreaResults;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public InterviewReportServiceImpl(
            InterviewSessionRepository sessions,
            InterviewAssessmentRepository assessments,
            InterviewFocusAreaResultRepository focusAreaResults,
            InterviewSessionTransitionRecorder transitionRecorder,
            ApplicationEventPublisher events,
            ObjectMapper objectMapper,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.assessments = assessments;
        this.focusAreaResults = focusAreaResults;
        this.transitionRecorder = transitionRecorder;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewReportResponse get(Long userId, Long sessionId) {
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
        requireReportState(session);
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            // Trả trạng thái gọn để frontend polling trong khi chưa có report hoàn chỉnh.
            return statusOnly(session);
        }

        InterviewAssessment assessment = assessments.findBySessionId(sessionId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_REPORT_NOT_AVAILABLE));
        List<InterviewReportResponse.FocusAreaScore> results = focusAreaResults
                .findByAssessmentIdOrderByFocusAreaDisplayOrderAsc(assessment.getId())
                .stream()
                .map(this::toFocusAreaScore)
                .toList();

        InterviewReportResponse.Report report = new InterviewReportResponse.Report(
                assessment.getOverallScore(),
                assessment.getOverallSummary(),
                new InterviewReportResponse.ScoreBreakdown(
                        new InterviewReportResponse.ScoreFeedback(
                                assessment.getTechnicalScore(),
                                assessment.getTechnicalFeedback()),
                        new InterviewReportResponse.ScoreFeedback(
                                assessment.getCommunicationScore(),
                                assessment.getCommunicationFeedback())),
                results,
                recommendations(assessment.getRecommendationsJson()));

        return new InterviewReportResponse(
                session.getId(),
                session.getStatus(),
                null,
                null,
                report,
                session.getCompletedAt());
    }

    @Override
    public InterviewReportResponse retryScoring(Long userId, Long sessionId) {
        transactions.executeWithoutResult(status -> {
            InterviewSession session = sessions.findOwnedByIdForUpdate(sessionId, userId)
                    .orElseThrow(() -> new DomainException(
                            ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
            if (session.getStatus() != InterviewSessionStatus.SCORING_FAILED) {
                throw new DomainException(ErrorCode.INTERVIEW_SCORING_NOT_RETRYABLE);
            }
            Instant now = clock.instant();
            session.retryScoring(now);
            transitionRecorder.record(
                    session,
                    InterviewSessionStatus.SCORING_FAILED,
                    InterviewSessionStatus.SCORING,
                    "User retried interview scoring",
                    InterviewTransitionActor.USER,
                    now);
            // Listener AFTER_COMMIT chỉ dispatch retry sau khi SCORING đã được lưu.
            events.publishEvent(new InterviewScoringRequestedEvent(sessionId));
        });
        return get(userId, sessionId);
    }

    private void requireReportState(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.SCORING
                && session.getStatus() != InterviewSessionStatus.SCORING_FAILED
                && session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new DomainException(ErrorCode.INTERVIEW_REPORT_NOT_AVAILABLE);
        }
    }

    private InterviewReportResponse statusOnly(InterviewSession session) {
        return new InterviewReportResponse(
                session.getId(),
                session.getStatus(),
                session.getScoringErrorCode(),
                session.getScoringErrorMessage(),
                null,
                session.getCompletedAt());
    }

    private InterviewReportResponse.FocusAreaScore toFocusAreaScore(
            InterviewFocusAreaResult result) {
        return new InterviewReportResponse.FocusAreaScore(
                result.getFocusArea().getName(),
                result.getScore());
    }

    private List<String> recommendations(String json) {
        return List.of(objectMapper.readValue(json, String[].class));
    }
}
