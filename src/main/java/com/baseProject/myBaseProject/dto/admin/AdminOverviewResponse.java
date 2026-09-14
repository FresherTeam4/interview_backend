package com.baseProject.myBaseProject.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminOverviewResponse(
        Instant generatedAt,
        int periodDays,
        UserMetrics users,
        SessionMetrics sessions,
        TemplateMetrics templates) {

    public record UserMetrics(
            long total,
            long enabled,
            long newInPeriod) {
    }

    public record SessionMetrics(
            long totalInPeriod,
            long completed,
            long inProgress,
            long preparationFailed,
            long scoringFailed,
            BigDecimal completionRate) {
    }

    public record TemplateMetrics(long published) {
    }
}
