package com.baseProject.myBaseProject.interview.ai;

import com.baseProject.myBaseProject.enums.FollowUpDecision;

public record GeneratedFollowUpDecision(
        FollowUpDecision decision,
        String questionText,
        String evidenceQuote,
        String reason) {
}
