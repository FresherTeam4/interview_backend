package com.baseProject.myBaseProject.interview.generation.model;

import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationInput;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;

import java.util.List;
import java.util.Set;

public record GenerationPreparation(
        Long profileId,
        String jobDescriptionHash,
        ScriptGenerationInput input,
        Set<Long> allowedProjectIds,
        Set<Long> allowedSkillIds,
        List<Long> recentSessionIds,
        List<QuestionHistoryProjection> history) {
}
