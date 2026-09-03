package com.baseProject.myBaseProject.interview.ai.model;

import com.baseProject.myBaseProject.enums.FollowUpDecision;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;

/** Input, provider output and metadata for one adaptive follow-up call. */
public final class FollowUpDecisionContract {

    private FollowUpDecisionContract() {
    }

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

    public record FollowUpDecisionOutcome(
            GeneratedFollowUpDecision result,
            String modelName,
            String promptVersion,
            Integer tokenCost,
            int durationMs) {
    }

    public record FollowUpTurnContext(
            TurnRole role,
            String content,
            boolean followUp,
            short followUpDepth) {

        public FollowUpTurnContext {
            Objects.requireNonNull(role);
            Objects.requireNonNull(content);
        }
    }

    public record GeneratedFollowUpDecision(
            FollowUpDecision decision,
            String questionText,
            String evidenceQuote,
            String reason) {
    }
}
