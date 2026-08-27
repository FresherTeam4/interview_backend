package com.baseProject.myBaseProject.interview;

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
