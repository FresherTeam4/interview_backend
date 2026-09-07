package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;

public record InterviewTurnContext(
        int turnIndex,
        InterviewTurnRole role,
        String content,
        CandidateIntent candidateIntent,
        InterviewTurnAction action,
        String focusAreaCode) {
}
