package com.baseProject.myBaseProject.interview.ai;

import com.baseProject.myBaseProject.enums.QuestionSourceType;

public record GeneratedQuestion(
        Integer ordinal,
        String questionText,
        String topic,
        String competency,
        Integer difficulty,
        QuestionSourceType sourceType,
        Long sourceProjectId,
        Long sourceSkillId,
        String sourceJdExcerpt,
        String signatureConcept) {
}
