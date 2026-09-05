package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;

public record InterviewFocusAreaResponse(
        String code,
        String name,
        String description,
        InterviewFocusPriority priority,
        String reason,
        Integer plannedSeconds,
        InterviewEvidenceStatus evidenceStatus,
        short displayOrder) {
}
