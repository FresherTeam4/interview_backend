package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.report.InterviewReportResponse;

public interface InterviewReportService {

    InterviewReportResponse get(Long userId, Long sessionId);
}
