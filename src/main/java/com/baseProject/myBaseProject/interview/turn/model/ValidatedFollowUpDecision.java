package com.baseProject.myBaseProject.interview.turn.model;

import com.baseProject.myBaseProject.enums.FollowUpDecision;

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
