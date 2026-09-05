package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.enums.InterviewerStyle;

public interface InterviewPlanningService {
    InterviewPlanResult generate(
            String templateSnapshotJson,
            String profileSnapshotJson,
            String languageCode,
            int durationMinutes,
            InterviewerStyle interviewerStyle);
}
