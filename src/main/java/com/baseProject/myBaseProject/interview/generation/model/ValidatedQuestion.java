package com.baseProject.myBaseProject.interview.generation.model;

import com.baseProject.myBaseProject.enums.QuestionSourceType;

public record ValidatedQuestion(
        short ordinal,
        String questionText,
        String topic,
        String competency,
        short difficulty,
        QuestionSourceType sourceType,
        Long sourceProjectId,
        Long sourceSkillId,
        String sourceJdExcerpt,
        String questionSignature) {
}
