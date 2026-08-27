package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.enums.FollowUpDecision;

record ValidatedFollowUpDecision(
        FollowUpDecision decision,
        String questionText,
        String evidenceQuote,
        boolean serverOverridden) {

    static ValidatedFollowUpDecision followUp(
            String questionText,
            String evidenceQuote) {
        return new ValidatedFollowUpDecision(
                FollowUpDecision.FOLLOW_UP,
                questionText,
                evidenceQuote,
                false);
    }

    static ValidatedFollowUpDecision nextQuestion(boolean serverOverridden) {
        return new ValidatedFollowUpDecision(
                FollowUpDecision.NEXT_QUESTION,
                null,
                null,
                serverOverridden);
    }
}
