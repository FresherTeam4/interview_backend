package com.baseProject.myBaseProject.interview.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionReport;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.ReportResultStatus;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.ReportHighlightRepository;
import com.baseProject.myBaseProject.repository.RubricCriterionRepository;
import com.baseProject.myBaseProject.repository.ScoreEvidenceRepository;
import com.baseProject.myBaseProject.repository.SessionReportRepository;
import com.baseProject.myBaseProject.repository.SessionScoreRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@ExtendWith(MockitoExtension.class)
class InterviewScoringStoreTest {

    private static final Long SESSION_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionTurnRepository turnRepository;
    @Mock
    private RubricCriterionRepository criterionRepository;
    @Mock
    private SessionScoreRepository scoreRepository;
    @Mock
    private ScoreEvidenceRepository evidenceRepository;
    @Mock
    private SessionReportRepository reportRepository;
    @Mock
    private ReportHighlightRepository highlightRepository;
    @Mock
    private SessionStateMachine stateMachine;
    @Mock
    private InterviewSession scoring;
    @Mock
    private InterviewSession completed;

    private InterviewScoringStore store;

    @BeforeEach
    void setUp() {
        store = new InterviewScoringStore(
                sessionRepository,
                turnRepository,
                criterionRepository,
                scoreRepository,
                evidenceRepository,
                reportRepository,
                highlightRepository,
                stateMachine,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void noAnswersCreatesOnlyInsufficientEvidenceReport() {
        when(scoring.getId()).thenReturn(SESSION_ID);
        when(scoring.getStatus()).thenReturn(SessionStatus.SCORING);
        when(scoring.getAwaitingAction()).thenReturn(AwaitingAction.REPORT);
        when(scoring.getProcessingStage()).thenReturn(SessionProcessingStage.SCORING);
        when(scoring.getAnsweredQuestionCount()).thenReturn((short) 0);
        when(scoring.getLanguageCode()).thenReturn("vi");
        when(stateMachine.completeScoringWithoutEvidence(
                        scoring,
                        "Insufficient-evidence report committed"))
                .thenReturn(completed);

        assertThat(store.commitInsufficientEvidence(scoring)).isSameAs(completed);

        ArgumentCaptor<SessionReport> reportCaptor =
                ArgumentCaptor.forClass(SessionReport.class);
        verify(reportRepository).save(reportCaptor.capture());
        SessionReport report = reportCaptor.getValue();
        assertThat(report.getResultStatus())
                .isEqualTo(ReportResultStatus.INSUFFICIENT_EVIDENCE);
        assertThat(report.getOverallScore()).isNull();
        assertThat(report.getCompletionRatio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.getAssessedWeight()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.getGeneratedAt()).isEqualTo(NOW);
        verifyNoInteractions(evidenceRepository, highlightRepository);
    }
}
