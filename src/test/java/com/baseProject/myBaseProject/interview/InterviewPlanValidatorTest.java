package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.validation.InterviewPlanValidator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewPlanValidatorTest {
    private final InterviewPlanValidator validator = new InterviewPlanValidator();

    @Test
    void acceptsAndNormalizesAValidPlan() {
        InterviewPlanResult result = validator.validate(new InterviewPlanResult(
                "VI",
                "  Backend Java role  ",
                "  Candidate has Spring experience  ",
                List.of(
                        area(" java_spring ", 300),
                        area("DATABASE", 240)),
                "  Chào bạn, hãy giới thiệu về kinh nghiệm phù hợp nhất của bạn.  "),
                "vi", 15);

        assertThat(result.languageCode()).isEqualTo("vi");
        assertThat(result.jobContextSummary()).isEqualTo("Backend Java role");
        assertThat(result.focusAreas()).extracting(InterviewPlanResult.FocusArea::code)
                .containsExactly("JAVA_SPRING", "DATABASE");
        assertThat(result.openingMessage()).startsWith("Chào bạn");
    }

    @Test
    void rejectsDuplicateFocusAreaCodes() {
        InterviewPlanResult plan = new InterviewPlanResult(
                "vi", "Job", "Candidate",
                List.of(area("DATABASE", 120), area("database", 120)),
                "Chào bạn, hãy giới thiệu về bản thân.");

        assertThatThrownBy(() -> validator.validate(plan, "vi", 15))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_PLAN_INVALID));
    }

    @Test
    void rejectsPlanWhoseTimeExceedsSessionDuration() {
        InterviewPlanResult plan = new InterviewPlanResult(
                "vi", "Job", "Candidate",
                List.of(area("JAVA", 600), area("DATABASE", 600)),
                "Chào bạn, hãy giới thiệu về bản thân.");

        assertThatThrownBy(() -> validator.validate(plan, "vi", 15))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_PLAN_INVALID));
    }

    @Test
    void rejectsPlanForAnotherLanguage() {
        InterviewPlanResult plan = new InterviewPlanResult(
                "en", "Job", "Candidate",
                List.of(area("JAVA", 120), area("DATABASE", 120)),
                "Tell me about yourself.");

        assertThatThrownBy(() -> validator.validate(plan, "vi", 15))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_PLAN_INVALID));
    }

    private InterviewPlanResult.FocusArea area(String code, int plannedSeconds) {
        return new InterviewPlanResult.FocusArea(
                code, "Focus area", "What evidence to collect",
                InterviewFocusPriority.HIGH, "Relevant to JD and CV", plannedSeconds);
    }
}
