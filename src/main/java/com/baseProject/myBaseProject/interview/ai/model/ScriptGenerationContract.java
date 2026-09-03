package com.baseProject.myBaseProject.interview.ai.model;

import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.QuestionSourceType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Input, provider output and metadata for one script-generation call. */
public final class ScriptGenerationContract {

    private ScriptGenerationContract() {
    }

    public record ScriptGenerationInput(
            String languageCode,
            InterviewDifficulty difficulty,
            int questionCount,
            UUID generationSeed,
            JsonNode profile,
            String jobDescription,
            Set<String> excludedQuestionSignatures) {

        public ScriptGenerationInput {
            Objects.requireNonNull(languageCode);
            Objects.requireNonNull(difficulty);
            Objects.requireNonNull(generationSeed);
            Objects.requireNonNull(profile);
            Objects.requireNonNull(jobDescription);
            profile = profile.deepCopy();
            excludedQuestionSignatures = excludedQuestionSignatures == null
                    ? Set.of()
                    : Set.copyOf(excludedQuestionSignatures);
        }

        @Override
        public JsonNode profile() {
            return profile.deepCopy();
        }
    }

    public record ScriptGenerationOutcome(
            GeneratedScript script,
            String modelName,
            String promptVersion,
            Integer tokenCost,
            int durationMs) {
    }

    public record GeneratedScript(List<GeneratedQuestion> questions) {

        public GeneratedScript {
            questions = questions == null ? null : List.copyOf(questions);
        }
    }

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
}
