package com.baseProject.myBaseProject.interview.turn.model;

import com.baseProject.myBaseProject.enums.FollowUpDecision;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionContract.FollowUpDecisionInput;

/** Internal data exchanged by the adaptive next-turn workflow. */
public final class NextTurnData {

    private NextTurnData() {
    }

    public enum NextTurnOutcome {
        FOLLOW_UP,
        NEXT_QUESTION,
        SCORING,
        IGNORED
    }

    public record NextTurnPreparation(
            Long candidateTurnId,
            Long questionId,
            FollowUpDecisionInput input) {
    }

    public record ValidatedFollowUpDecision(
            FollowUpDecision decision,
            String questionText,
            String evidenceQuote,
            boolean serverOverridden) {

        public static ValidatedFollowUpDecision followUp(
                String questionText,
                String evidenceQuote) {
            return new ValidatedFollowUpDecision(
                    FollowUpDecision.FOLLOW_UP,
                    questionText,
                    evidenceQuote,
                    false);
        }

        public static ValidatedFollowUpDecision nextQuestion(boolean serverOverridden) {
            return new ValidatedFollowUpDecision(
                    FollowUpDecision.NEXT_QUESTION,
                    null,
                    null,
                    serverOverridden);
        }
    }
}
