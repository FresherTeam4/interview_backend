package com.baseProject.myBaseProject.dto.question;

import java.util.List;

import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;

public record QuestionFilter(
        String keyword,
        Boolean active,
        List<Integer> techStackIds,
        Boolean unclassified,
        List<Integer> technologyIds,
        List<QuestionLevel> levels,
        List<QuestionType> questionTypes,
        List<QuestionDifficulty> difficulties
) {
    public static QuestionFilter empty() {
        return new QuestionFilter(null, null, null, null, null, null, null, null);
    }
}
