package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.enums.InterviewerStyle;

public record RealtimeSessionSpec(
        Long interviewSessionId,
        String languageCode,
        int durationMinutes,
        InterviewerStyle interviewerStyle,
        String voiceName,
        String systemInstruction) {
}
