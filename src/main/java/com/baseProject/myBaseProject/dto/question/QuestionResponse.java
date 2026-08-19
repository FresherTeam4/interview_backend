package com.baseProject.myBaseProject.dto.question;

import java.time.Instant;
import java.util.List;

import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;

public record QuestionResponse(
        Long id,
        String contentVi,
        String contentEn,
        List<TechStackSummaryResponse> techStacks,
        List<TechnologySummaryResponse> technologies,
        QuestionLevel level,
        QuestionType questionType,
        QuestionDifficulty difficulty,
        String companyRef,
        Long createdById,
        boolean active,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
}
