package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.InterviewAssessment;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewFocusAreaResult;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewScoringRequestedEvent;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewAssessmentRepository;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaResultRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewReportServiceImplTest {
    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 501L;
    private static final Instant NOW = Instant.parse("2026-09-07T08:00:00Z");

    @Test
    void returnsCompletedReportWithOrderedFocusResults() {
        Fixture fixture = new Fixture(completedSession());
        InterviewAssessment assessment = fixture.assessment();
        InterviewFocusAreaResult result = fixture.focusResult(assessment);
        when(fixture.assessments.findBySessionId(SESSION_ID))
                .thenReturn(Optional.of(assessment));
        when(fixture.focusAreaResults
                .findByAssessmentIdOrderByFocusAreaDisplayOrderAsc(91L))
                .thenReturn(List.of(result));

        var response = fixture.service.get(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.COMPLETED);
        assertThat(response.report().score()).isEqualByComparingTo("74.00");
        assertThat(response.report().scores().technical()).satisfies(score -> {
            assertThat(score.score()).isEqualByComparingTo("75.00");
            assertThat(score.feedback()).isEqualTo(
                    "Nắm kiến thức chính nhưng cần giải thích trade-off.");
        });
        assertThat(response.report().scores().communication()).satisfies(score -> {
            assertThat(score.score()).isEqualByComparingTo("70.00");
            assertThat(score.feedback()).isEqualTo("Trình bày rõ ràng.");
        });
        assertThat(response.report().recommendations())
                .containsExactly("Bổ sung kết quả định lượng.");
        assertThat(response.report().focusAreas()).singleElement().satisfies(area -> {
            assertThat(area.name()).isEqualTo("Backend");
            assertThat(area.score()).isEqualByComparingTo("75.00");
        });
    }

    @Test
    void returnsStatusWithoutReportWhileScoring() {
        InterviewSession session = session(InterviewSessionStatus.SCORING);
        Fixture fixture = new Fixture(session);

        var response = fixture.service.get(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.SCORING);
        assertThat(response.report()).isNull();
    }

    @Test
    void retriesFailedScoringAndPublishesNewRequest() {
        InterviewSession session = session(InterviewSessionStatus.SCORING_FAILED);
        session.markScoringFailed("AI_TIMEOUT", "AI timed out", NOW.minusSeconds(10));
        Fixture fixture = new Fixture(session);
        when(fixture.sessions.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));

        var response = fixture.service.retryScoring(USER_ID, SESSION_ID);

        assertThat(response.status()).isEqualTo(InterviewSessionStatus.SCORING);
        assertThat(session.getScoringErrorCode()).isNull();
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(fixture.events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(
                new InterviewScoringRequestedEvent(SESSION_ID));
        verify(fixture.transitions).record(
                session,
                InterviewSessionStatus.SCORING_FAILED,
                InterviewSessionStatus.SCORING,
                "User retried interview scoring",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.USER,
                NOW);
    }

    @Test
    void rejectsRetryUnlessScoringHasFailed() {
        InterviewSession session = session(InterviewSessionStatus.SCORING);
        Fixture fixture = new Fixture(session);
        when(fixture.sessions.findOwnedByIdForUpdate(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> fixture.service.retryScoring(USER_ID, SESSION_ID))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ErrorCode.INTERVIEW_SCORING_NOT_RETRYABLE));
    }

    @Test
    void rejectsReportBeforeInterviewEnds() {
        Fixture fixture = new Fixture(session(InterviewSessionStatus.IN_PROGRESS));

        assertThatThrownBy(() -> fixture.service.get(USER_ID, SESSION_ID))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ErrorCode.INTERVIEW_REPORT_NOT_AVAILABLE));
    }

    private static InterviewSession completedSession() {
        InterviewSession session = session(InterviewSessionStatus.SCORING);
        session.markScoringCompleted(NOW);
        return session;
    }

    private static InterviewSession session(InterviewSessionStatus status) {
        return InterviewSession.builder()
                .id(SESSION_ID)
                .status(status)
                .createdAt(NOW.minusSeconds(1800))
                .updatedAt(NOW.minusSeconds(10))
                .build();
    }

    private static PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static final class Fixture {
        private final InterviewSession session;
        private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        private final InterviewAssessmentRepository assessments = mock(InterviewAssessmentRepository.class);
        private final InterviewFocusAreaResultRepository focusAreaResults = mock(InterviewFocusAreaResultRepository.class);
        private final InterviewSessionTransitionRecorder transitions = mock(InterviewSessionTransitionRecorder.class);
        private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final InterviewReportServiceImpl service;

        private Fixture(InterviewSession session) {
            this.session = session;
            when(sessions.findByIdAndUserId(SESSION_ID, USER_ID))
                    .thenReturn(Optional.of(session));
            this.service = new InterviewReportServiceImpl(
                    sessions,
                    assessments,
                    focusAreaResults,
                    transitions,
                    events,
                    objectMapper,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    transactionManager());
        }

        private InterviewAssessment assessment() {
            return InterviewAssessment.builder()
                    .id(91L)
                    .session(session)
                    .technicalScore(new BigDecimal("75.00"))
                    .communicationScore(new BigDecimal("70.00"))
                    .overallScore(new BigDecimal("74.00"))
                    .coveragePercentage(new BigDecimal("100.00"))
                    .overallSummary("Ứng viên có kiến thức backend.")
                    .technicalFeedback(
                            "Nắm kiến thức chính nhưng cần giải thích trade-off.")
                    .recommendationsJson(objectMapper.writeValueAsString(
                            List.of("Bổ sung kết quả định lượng.")))
                    .communicationFeedback("Trình bày rõ ràng.")
                    .schemaVersion("v2")
                    .modelName("test-model")
                    .promptVersion("v3")
                    .createdAt(NOW)
                    .build();
        }

        private InterviewFocusAreaResult focusResult(InterviewAssessment assessment) {
            InterviewFocusArea area = InterviewFocusArea.builder()
                    .id(21L)
                    .session(session)
                    .code("BACKEND")
                    .name("Backend")
                    .priority(InterviewFocusPriority.HIGH)
                    .displayOrder((short) 0)
                    .build();
            return InterviewFocusAreaResult.builder()
                    .id(101L)
                    .assessment(assessment)
                    .focusArea(area)
                    .score(new BigDecimal("75.00"))
                    .evidenceStatus(InterviewEvidenceStatus.SUFFICIENT)
                    .evidenceTurnIdsJson("[11]")
                    .build();
        }
    }
}
