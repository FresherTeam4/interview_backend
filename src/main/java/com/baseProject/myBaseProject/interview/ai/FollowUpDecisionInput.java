package com.baseProject.myBaseProject.interview.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

public record FollowUpDecisionInput(
        String languageCode,
        String baseQuestion,
        String topic,
        String competency,
        JsonNode relevantProfileContext,
        String relevantJobDescriptionExcerpt,
        List<FollowUpTurnContext> currentQuestionTurns,
        String candidateAnswer,
        int remainingQuestionFollowUps,
        int remainingSessionFollowUps) {

    public FollowUpDecisionInput {
        Objects.requireNonNull(languageCode);
        Objects.requireNonNull(baseQuestion);
        Objects.requireNonNull(topic);
        Objects.requireNonNull(competency);
        Objects.requireNonNull(relevantProfileContext);
        Objects.requireNonNull(currentQuestionTurns);
        Objects.requireNonNull(candidateAnswer);
        relevantProfileContext = relevantProfileContext.deepCopy();
        currentQuestionTurns = List.copyOf(currentQuestionTurns);
        if (remainingQuestionFollowUps < 0 || remainingSessionFollowUps < 0) {
            throw new IllegalArgumentException("Follow-up budgets must not be negative");
        }
    }

    @Override
    public JsonNode relevantProfileContext() {
        return relevantProfileContext.deepCopy();
    }
}
