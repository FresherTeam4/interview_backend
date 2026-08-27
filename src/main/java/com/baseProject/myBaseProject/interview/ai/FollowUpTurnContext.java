package com.baseProject.myBaseProject.interview.ai;

import com.baseProject.myBaseProject.enums.TurnRole;

import java.util.Objects;

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
