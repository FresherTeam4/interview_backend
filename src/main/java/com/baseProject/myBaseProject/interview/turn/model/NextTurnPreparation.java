package com.baseProject.myBaseProject.interview.turn.model;

import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionInput;

public record NextTurnPreparation(
        Long candidateTurnId,
        Long questionId,
        FollowUpDecisionInput input) {
}
