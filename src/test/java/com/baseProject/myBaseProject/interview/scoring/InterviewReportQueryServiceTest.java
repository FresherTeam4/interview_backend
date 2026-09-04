package com.baseProject.myBaseProject.interview.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.dto.report.InterviewReportResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.ReportHighlight;
import com.baseProject.myBaseProject.entity.ScoreEvidence;
import com.baseProject.myBaseProject.entity.SessionReport;
import com.baseProject.myBaseProject.entity.SessionScore;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.ReportHighlightType;
import com.baseProject.myBaseProject.enums.ReportResultStatus;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.ReportNotReadyException;
import com.baseProject.myBaseProject.exception.SessionRetryRequiredException;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.ReportHighlightRepository;
import com.baseProject.myBaseProject.repository.ScoreEvidenceRepository;
import com.baseProject.myBaseProject.repository.SessionReportRepository;
import com.baseProject.myBaseProject.repository.SessionScoreRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class InterviewReportQueryServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private SessionReportRepository reportRepository;
    @Mock
    private SessionScoreRepository scoreRepository;
    @Mock
    private ScoreEvidenceRepository evidenceRepository;
    @Mock
    private ReportHighlightRepository highlightRepository;
    @Mock
    private InterviewSession session;
    @Mock
    private SessionReport report;
    @Mock
    private SessionScore score;
    @Mock
    private ScoreEvidence evidence;
    @Mock
    private SessionTurn turn;
    @Mock
    private ReportHighlight strength;

    private InterviewReportQueryService service;

    @BeforeEach
    void setUp() {
        service = new InterviewReportQueryService(
                sessionRepository,
                reportRepository,
                scoreRepository,
                evidenceRepository,
                highlightRepository);
        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID))
                .thenReturn(Optional.of(session));
    }

    @Test
    void getRejectsReportWhileScoringIsRunning() {
        when(session.getStatus()).thenReturn(SessionStatus.SCORING);
        when(reportRepository.findOwned(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, SESSION_ID))
                .isInstanceOf(ReportNotReadyException.class);
    }

    @Test
    void getRequiresRetryAfterScoringFailure() {
        when(session.getStatus()).thenReturn(SessionStatus.FAILED);
        when(session.getFailureStage()).thenReturn(SessionFailureStage.SCORING);
        when(reportRepository.findOwned(SESSION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, SESSION_ID))
                .isInstanceOf(SessionRetryRequiredException.class);
    }

    @Test
    void getReturnsEmptyScoresForInsufficientEvidence() {
        Instant generatedAt = Instant.parse("2026-08-26T08:00:00Z");
        when(reportRepository.findOwned(SESSION_ID, USER_ID)).thenReturn(Optional.of(report));
        when(report.getId()).thenReturn(51L);
        when(report.getResultStatus()).thenReturn(ReportResultStatus.INSUFFICIENT_EVIDENCE);
        when(report.isPartial()).thenReturn(true);
        when(report.getCompletionRatio()).thenReturn(new BigDecimal("0.0000"));
        when(report.getAssessedWeight()).thenReturn(new BigDecimal("0.000"));
        when(report.getSummaryText()).thenReturn("Chưa có câu trả lời");
        when(report.getDisclaimer()).thenReturn(InterviewScoringStore.DISCLAIMER);
        when(report.getGeneratedAt()).thenReturn(generatedAt);
        when(scoreRepository.findBySessionIdInDisplayOrder(SESSION_ID)).thenReturn(List.of());
        when(highlightRepository.findByReportIdOrderByTypeAscDisplayOrderAsc(51L))
                .thenReturn(List.of());

        InterviewReportResponse response = service.get(USER_ID, SESSION_ID);

        assertThat(response.resultStatus())
                .isEqualTo(ReportResultStatus.INSUFFICIENT_EVIDENCE);
        assertThat(response.overallScore()).isNull();
        assertThat(response.criteria()).isEmpty();
        assertThat(response.strengths()).isEmpty();
        assertThat(response.generatedAt()).isEqualTo(generatedAt);
    }

    @Test
    void getMapsStoredScoresEvidenceAndHighlights() {
        Instant generatedAt = Instant.parse("2026-08-26T08:00:00Z");
        when(reportRepository.findOwned(SESSION_ID, USER_ID)).thenReturn(Optional.of(report));
        when(report.getId()).thenReturn(51L);
        when(report.getResultStatus()).thenReturn(ReportResultStatus.SCORED);
        when(report.getOverallScore()).thenReturn(new BigDecimal("75.00"));
        when(report.isPartial()).thenReturn(true);
        when(report.getCompletionRatio()).thenReturn(new BigDecimal("0.5000"));
        when(report.getAssessedWeight()).thenReturn(new BigDecimal("0.600"));
        when(report.getSummaryText()).thenReturn("Tóm tắt");
        when(report.getDisclaimer()).thenReturn(InterviewScoringStore.DISCLAIMER);
        when(report.getGeneratedAt()).thenReturn(generatedAt);
        when(score.getId()).thenReturn(61L);
        when(score.getCriterionCode()).thenReturn("TECHNICAL_DEPTH");
        when(score.getCriterionName()).thenReturn("Độ sâu kỹ thuật");
        when(score.getScore()).thenReturn(new BigDecimal("3.00"));
        when(score.getMaxScore()).thenReturn(new BigDecimal("4.00"));
        when(score.getLevelNo()).thenReturn((short) 3);
        when(score.getComment()).thenReturn("Nhận xét");
        when(evidence.getSessionScore()).thenReturn(score);
        when(evidence.getTurn()).thenReturn(turn);
        when(evidence.getQuoteText()).thenReturn("chọn Redis");
        when(evidence.getStartOffset()).thenReturn(3);
        when(evidence.getEndOffset()).thenReturn(13);
        when(turn.getId()).thenReturn(101L);
        when(strength.getType()).thenReturn(ReportHighlightType.STRENGTH);
        when(strength.getContent()).thenReturn("Nền tảng tốt");
        when(scoreRepository.findBySessionIdInDisplayOrder(SESSION_ID))
                .thenReturn(List.of(score));
        when(evidenceRepository.findByScoreIds(List.of(61L))).thenReturn(List.of(evidence));
        when(highlightRepository.findByReportIdOrderByTypeAscDisplayOrderAsc(51L))
                .thenReturn(List.of(strength));

        InterviewReportResponse response = service.get(USER_ID, SESSION_ID);

        assertThat(response.overallScore()).isEqualByComparingTo("75.00");
        assertThat(response.criteria()).singleElement().satisfies(criterion -> {
            assertThat(criterion.code()).isEqualTo("TECHNICAL_DEPTH");
            assertThat(criterion.evidences()).singleElement().satisfies(item -> {
                assertThat(item.turnId()).isEqualTo(101L);
                assertThat(item.quote()).isEqualTo("chọn Redis");
            });
        });
        assertThat(response.strengths()).containsExactly("Nền tảng tốt");
        assertThat(response.improvements()).isEmpty();
    }
}
