package com.baseProject.myBaseProject.dto.session;

import java.util.List;

public record InterviewSessionOptionsResponse(
        List<InterviewOptionResponse> languages,
        List<Integer> durations,
        List<InterviewOptionResponse> interviewerStyles,
        List<InterviewOptionResponse> modes) {
}
