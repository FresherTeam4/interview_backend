package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.report.InterviewReportResponse;
import com.baseProject.myBaseProject.interview.scoring.InterviewReportQueryService;
import com.baseProject.myBaseProject.service.InterviewReportService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewReportServiceImpl implements InterviewReportService {

    private final InterviewReportQueryService queryService;

    @Override
    public InterviewReportResponse get(Long userId, Long sessionId) {
        return queryService.get(userId, sessionId);
    }
}
