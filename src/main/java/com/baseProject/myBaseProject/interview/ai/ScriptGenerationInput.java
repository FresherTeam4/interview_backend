package com.baseProject.myBaseProject.interview.ai;

import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
