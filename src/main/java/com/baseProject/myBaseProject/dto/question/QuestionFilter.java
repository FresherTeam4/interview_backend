package com.baseProject.myBaseProject.dto.question;

import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;

public record QuestionFilter(
        String keyword,
        Boolean active,
        Integer techStackId,
        Boolean unclassified,
        QuestionLevel level,
        QuestionType questionType,
        QuestionDifficulty difficulty
) {
    public static QuestionFilter empty() {
        return new QuestionFilter(null, null, null, null, null, null, null);
    }
}
