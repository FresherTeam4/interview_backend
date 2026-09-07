package com.baseProject.myBaseProject.interview.model;

import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;

public record InterviewTurnContext(
        int turnIndex,
        InterviewTurnRole role,
        String content,
        InterviewTurnAction action,
        String focusAreaCode) {
}
