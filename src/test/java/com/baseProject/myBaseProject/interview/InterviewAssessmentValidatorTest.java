package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.validation.InterviewAssessmentValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewAssessmentValidatorTest {
    private final InterviewAssessmentValidator validator =
            new InterviewAssessmentValidator();

    @Test
    void normalizesCompactAssessmentAndKeepsCandidateEvidence() {
        InterviewAssessmentResult result = validator.validate(
                validAssessment(), context());

        assertThat(result.overallSummary()).isEqualTo("Ứng viên có nền tảng backend.");
        assertThat(result.technicalFeedback()).isEqualTo(
                "Nắm kiến thức chính nhưng cần giải thích trade-off.");
        assertThat(result.focusAreaAssessments()).extracting(
                        InterviewAssessmentResult.FocusAreaAssessment::focusAreaCode)
                .containsExactly("BACKEND", "DATABASE");
        assertThat(result.focusAreaAssessments().get(0).evidenceTurnIds())
                .containsExactly(11L);
        assertThat(result.recommendations())
                .containsExactly("Luyện giải thích trade-off bằng ví dụ thực tế.");
    }

    @Test
    void rejectsUnknownFocusArea() {
        InterviewAssessmentResult invalid = replaceSecondFocus(
                focus("UNKNOWN", 60, InterviewEvidenceStatus.PARTIAL, List.of(13L)));

        assertInvalid(invalid);
    }

    @Test
    void rejectsInterviewerTurnAsEvidence() {
        InterviewAssessmentResult invalid = replaceFirstFocus(
                focus("BACKEND", 70, InterviewEvidenceStatus.SUFFICIENT, List.of(10L)));

        assertInvalid(invalid);
    }

    @Test
    void rejectsScoreForUnexploredFocusArea() {
        InterviewAssessmentResult invalid = replaceFirstFocus(
                focus("BACKEND", 20, InterviewEvidenceStatus.NOT_EXPLORED, List.of()));

        assertInvalid(invalid);
    }

    @Test
    void rejectsScoredFocusAreaWithoutCandidateEvidence() {
        InterviewAssessmentResult invalid = replaceFirstFocus(
                focus("BACKEND", 70, InterviewEvidenceStatus.PARTIAL, List.of()));

        assertInvalid(invalid);
    }

    @Test
    void acceptsUnexploredFocusAreaWithoutScoreOrEvidence() {
        InterviewAssessmentResult valid = replaceSecondFocus(
                focus("DATABASE", null, InterviewEvidenceStatus.NOT_EXPLORED, List.of()));

        InterviewAssessmentResult result = validator.validate(valid, context());

        assertThat(result.focusAreaAssessments().get(1).score()).isNull();
    }

    @Test
    void rejectsMoreThanThreeRecommendations() {
        InterviewAssessmentResult valid = validAssessment();
        InterviewAssessmentResult invalid = new InterviewAssessmentResult(
                valid.overallSummary(),
                valid.technicalFeedback(),
                valid.focusAreaAssessments(),
                valid.communicationScore(),
                valid.communicationFeedback(),
                List.of("Một", "Hai", "Ba", "Bốn"));

        assertInvalid(invalid);
    }

    private void assertInvalid(InterviewAssessmentResult result) {
        assertThatThrownBy(() -> validator.validate(result, context()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ErrorCode.INTERVIEW_ASSESSMENT_INVALID));
    }

    private InterviewAssessmentResult replaceFirstFocus(
            InterviewAssessmentResult.FocusAreaAssessment replacement) {
        InterviewAssessmentResult valid = validAssessment();
        return new InterviewAssessmentResult(
                valid.overallSummary(),
                valid.technicalFeedback(),
                List.of(replacement, valid.focusAreaAssessments().get(1)),
                valid.communicationScore(),
                valid.communicationFeedback(),
                valid.recommendations());
    }

    private InterviewAssessmentResult replaceSecondFocus(
            InterviewAssessmentResult.FocusAreaAssessment replacement) {
        InterviewAssessmentResult valid = validAssessment();
        return new InterviewAssessmentResult(
                valid.overallSummary(),
                valid.technicalFeedback(),
                List.of(valid.focusAreaAssessments().get(0), replacement),
                valid.communicationScore(),
                valid.communicationFeedback(),
                valid.recommendations());
    }

    private InterviewAssessmentResult validAssessment() {
        return new InterviewAssessmentResult(
                "  Ứng viên có nền tảng backend.  ",
                "  Nắm kiến thức chính nhưng cần giải thích trade-off.  ",
                List.of(
                        focus(" backend ", 78, InterviewEvidenceStatus.SUFFICIENT,
                                List.of(11L)),
                        focus("database", 60, InterviewEvidenceStatus.PARTIAL,
                                List.of(13L))),
                72,
                "Trình bày rõ nhưng cần cấu trúc hơn.",
                List.of("  Luyện giải thích trade-off bằng ví dụ thực tế.  "));
    }

    private InterviewAssessmentResult.FocusAreaAssessment focus(
            String code,
            Integer score,
            InterviewEvidenceStatus evidenceStatus,
            List<Long> evidenceTurnIds) {
        return new InterviewAssessmentResult.FocusAreaAssessment(
                code, score, evidenceStatus, evidenceTurnIds);
    }

    private InterviewScoringContext context() {
        return new InterviewScoringContext(
                501L,
                InterviewSessionStatus.SCORING,
                "vi",
                InterviewEndReason.AI_COMPLETED,
                1200,
                null,
                "Backend Java",
                "Candidate discussed backend work.",
                List.of(
                        new InterviewScoringContext.FocusArea(
                                21L, "BACKEND", "Backend", "Spring Boot",
                                InterviewFocusPriority.HIGH, "Core requirement",
                                InterviewEvidenceStatus.SUFFICIENT, "REST API example"),
                        new InterviewScoringContext.FocusArea(
                                22L, "DATABASE", "Database", "SQL",
                                InterviewFocusPriority.MEDIUM, "Required",
                                InterviewEvidenceStatus.PARTIAL, "Limited example")),
                List.of(
                        new InterviewScoringContext.Turn(
                                10L, 0, InterviewTurnRole.INTERVIEWER,
                                "Hãy mô tả dự án backend.", null,
                                InterviewTurnAction.OPENING, null),
                        new InterviewScoringContext.Turn(
                                11L, 1, InterviewTurnRole.CANDIDATE,
                                "Tôi xây dựng REST API bằng Spring Boot.",
                                CandidateIntent.ANSWER, null, null),
                        new InterviewScoringContext.Turn(
                                12L, 2, InterviewTurnRole.INTERVIEWER,
                                "Bạn tối ưu SQL thế nào?", null,
                                InterviewTurnAction.EXPLORE, "DATABASE"),
                        new InterviewScoringContext.Turn(
                                13L, 3, InterviewTurnRole.CANDIDATE,
                                "Tôi đã thêm index cho truy vấn.",
                                CandidateIntent.ANSWER, null, null)));
    }
}
