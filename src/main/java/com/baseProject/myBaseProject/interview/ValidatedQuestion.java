package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.enums.QuestionSourceType;

record ValidatedQuestion(
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
