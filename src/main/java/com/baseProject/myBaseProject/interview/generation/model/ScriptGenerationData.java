package com.baseProject.myBaseProject.interview.generation.model;

import com.baseProject.myBaseProject.enums.QuestionSourceType;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationContract.ScriptGenerationInput;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;

import java.util.List;
import java.util.Set;

/** Internal data exchanged by the script-generation workflow. */
public final class ScriptGenerationData {

    private ScriptGenerationData() {
    }

    public record GenerationPreparation(
            Long profileId,
            String jobDescriptionHash,
            ScriptGenerationInput input,
            Set<Long> allowedProjectIds,
            Set<Long> allowedSkillIds,
            List<Long> recentSessionIds,
            List<QuestionHistoryProjection> history) {
    }

    public record ScriptGenerationResult(
            Long sessionId,
            int questionCount,
            long sessionVersion,
            String modelName,
            String promptVersion,
            Integer tokenCost,
            int durationMs,
            boolean diversityRetried) {
    }

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

    public record ValidatedScript(
            List<ValidatedQuestion> questions,
            String modelName,
            String promptVersion,
            Integer tokenCost,
            int durationMs) {

        public ValidatedScript {
            questions = List.copyOf(questions);
        }
    }
}
