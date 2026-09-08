package com.baseProject.myBaseProject.jobdescription.validation;

import com.baseProject.myBaseProject.jobdescription.JobAnalysisTestData;
import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobAnalysisValidatorTest {
    private final JobAnalysisValidator validator = new JobAnalysisValidator();

    @Test
    void acceptsValidJobAnalysis() {
        assertThat(validator.validate(JobAnalysisTestData.analysis()))
                .isNotNull();
    }

    @Test
    void acceptsEditableJobAnalysis() {
        assertThat(validator.validate(JobAnalysisTestData.analysis())).isNotNull();
    }

    @Test
    void rejectsInsufficientJobContent() {
        JobAnalysis insufficient = new JobAnalysis(
                false, "vi", null, null, null,
                "Không có nội dung công việc", List.of());
        assertThatThrownBy(() -> validator.validate(insufficient))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.TEMPLATE_INSUFFICIENT_JD));
    }

    @Test
    void rejectsEmptyKeySkills() {
        JobAnalysis emptySkills = new JobAnalysis(
                true, "vi", "Developer", "Junior", "IT",
                "Summary text", List.of());
        assertThatThrownBy(() -> validator.validate(emptySkills))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.TEMPLATE_INVALID_ANALYSIS));
    }

    @Test
    void rejectsDuplicateSkillNames() {
        JobAnalysis duplicateSkills = new JobAnalysis(
                true, "vi", "Developer", "Junior", "IT",
                "Summary text",
                List.of(
                        new JobAnalysis.KeySkill("Java", JobAnalysis.SkillLevel.MUST_HAVE, "Core Java"),
                        new JobAnalysis.KeySkill("java", JobAnalysis.SkillLevel.NICE_TO_HAVE, "Java EE")
                ));
        assertThatThrownBy(() -> validator.validate(duplicateSkills))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.TEMPLATE_INVALID_ANALYSIS));
    }
}
