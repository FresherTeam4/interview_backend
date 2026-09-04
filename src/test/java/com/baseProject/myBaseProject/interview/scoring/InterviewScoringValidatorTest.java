package com.baseProject.myBaseProject.interview.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseProject.myBaseProject.enums.SessionEndReason;
import com.baseProject.myBaseProject.exception.InterviewScoringException;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedCriterionScore;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedEvidence;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedScoringResult;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringOutcome;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.CriterionReference;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ScoringPreparation;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.TurnReference;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedScoringResult;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class InterviewScoringValidatorTest {

    private final InterviewScoringValidator validator = new InterviewScoringValidator();

    @Test
    void fullReportCalculatesWeightedScoreFromLockedRubric() {
        ScoringPreparation preparation = preparation(false, new BigDecimal("1.0000"));
        ScoringOutcome outcome = outcome(List.of(
                score("TECHNICAL_DEPTH", (short) 3, "3.00", 101L, "chọn Redis"),
                score("COMMUNICATION", (short) 2, "2.00", 102L, "giải thích rõ")));

        ValidatedScoringResult result = validator.validate(outcome, preparation, "v1");

        assertThat(result.overallScore()).isEqualByComparingTo("65.00");
        assertThat(result.assessedWeight()).isEqualByComparingTo("1.000");
        assertThat(result.partial()).isFalse();
        assertThat(result.criteria()).hasSize(2);
        assertThat(result.criteria().get(0).evidences().get(0).startOffset()).isEqualTo(3);
    }

    @Test
    void partialReportNormalizesScoreOverAssessedWeight() {
        ScoringPreparation preparation = preparation(true, new BigDecimal("0.5000"));
        ScoringOutcome outcome = outcome(List.of(
                score("TECHNICAL_DEPTH", (short) 3, "3.00", 101L, "chọn Redis")));

        ValidatedScoringResult result = validator.validate(outcome, preparation, "v1");

        assertThat(result.overallScore()).isEqualByComparingTo("75.00");
        assertThat(result.assessedWeight()).isEqualByComparingTo("0.600");
        assertThat(result.partial()).isTrue();
    }

    @Test
    void fakeEvidenceQuoteIsRejected() {
        ScoringPreparation preparation = preparation(true, new BigDecimal("0.5000"));
        ScoringOutcome outcome = outcome(List.of(
                score("TECHNICAL_DEPTH", (short) 3, "3.00", 101L, "quote không có")));

        assertThatThrownBy(() -> validator.validate(outcome, preparation, "v1"))
                .isInstanceOf(InterviewScoringException.class)
                .hasMessageContaining("evidence quote");
    }

    @Test
    void fullReportMustAssessEveryCriterion() {
        ScoringPreparation preparation = preparation(false, new BigDecimal("1.0000"));
        ScoringOutcome outcome = outcome(List.of(
                score("TECHNICAL_DEPTH", (short) 3, "3.00", 101L, "chọn Redis")));

        assertThatThrownBy(() -> validator.validate(outcome, preparation, "v1"))
                .isInstanceOf(InterviewScoringException.class)
                .hasMessageContaining("every criterion");
    }

    private ScoringPreparation preparation(boolean partial, BigDecimal completionRatio) {
        CriterionReference technical = new CriterionReference(
                11L,
                "TECHNICAL_DEPTH",
                "Technical depth",
                new BigDecimal("0.600"),
                new BigDecimal("4.00"),
                Map.of((short) 3, new BigDecimal("3.00")));
        CriterionReference communication = new CriterionReference(
                12L,
                "COMMUNICATION",
                "Communication",
                new BigDecimal("0.400"),
                new BigDecimal("4.00"),
                Map.of((short) 2, new BigDecimal("2.00")));
        return new ScoringPreparation(
                new ScoringInput(
                        "vi",
                        completionRatio,
                        partial
                                ? SessionEndReason.USER_COMPLETED_EARLY
                                : SessionEndReason.USER_COMPLETED,
                        List.of(),
                        List.of()),
                Map.of(
                        technical.code(), technical,
                        communication.code(), communication),
                Map.of(
                        101L, new TurnReference(
                                101L,
                                "Em chọn Redis vì cần truy cập nhanh"),
                        102L, new TurnReference(
                                102L,
                                "Em giải thích rõ từng trade-off")),
                partial);
    }

    private ScoringOutcome outcome(List<GeneratedCriterionScore> scores) {
        return new ScoringOutcome(
                new GeneratedScoringResult(
                        scores,
                        "Tóm tắt kết quả",
                        List.of("Có nền tảng tốt"),
                        List.of("Cần thêm số liệu"),
                        List.of("Luyện giải thích trade-off")),
                "gemini-test",
                "v1",
                100,
                250);
    }

    private GeneratedCriterionScore score(
            String code,
            short level,
            String score,
            Long turnId,
            String quote) {
        return new GeneratedCriterionScore(
                code,
                level,
                new BigDecimal(score),
                "Nhận xét có dẫn chứng",
                List.of(new GeneratedEvidence(turnId, quote)));
    }
}
