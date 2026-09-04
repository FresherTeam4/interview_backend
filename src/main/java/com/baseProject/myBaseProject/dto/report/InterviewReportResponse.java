package com.baseProject.myBaseProject.dto.report;

import com.baseProject.myBaseProject.enums.ReportResultStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InterviewReportResponse(
        Long sessionId,
        ReportResultStatus resultStatus,
        BigDecimal overallScore,
        boolean partial,
        BigDecimal completionRatio,
        BigDecimal assessedWeight,
        String summary,
        String disclaimer,
        List<CriterionScore> criteria,
        List<String> strengths,
        List<String> improvements,
        List<String> nextActions,
        Instant generatedAt) {

    public record CriterionScore(
            String code,
            String name,
            BigDecimal score,
            BigDecimal maxScore,
            short level,
            String comment,
            List<Evidence> evidences) {
    }

    public record Evidence(
            Long turnId,
            String quote,
            int startOffset,
            int endOffset) {
    }
}
