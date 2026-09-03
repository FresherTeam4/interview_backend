package com.baseProject.myBaseProject.interview.ai.model;

public record ScriptGenerationOutcome(
        GeneratedScript script,
        String modelName,
        String promptVersion,
        Integer tokenCost,
        int durationMs) {
}
