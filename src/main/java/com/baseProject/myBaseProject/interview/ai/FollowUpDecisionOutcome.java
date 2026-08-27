package com.baseProject.myBaseProject.interview.ai;

public record FollowUpDecisionOutcome(
        GeneratedFollowUpDecision result,
        String modelName,
        String promptVersion,
        Integer tokenCost,
        int durationMs) {
}
