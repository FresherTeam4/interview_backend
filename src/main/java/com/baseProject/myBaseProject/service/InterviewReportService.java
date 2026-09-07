package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.session.InterviewReportResponse;

public interface InterviewReportService {
    InterviewReportResponse get(Long userId, Long sessionId);

    InterviewReportResponse retryScoring(Long userId, Long sessionId);
}
