package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.admin.AdminOverviewResponse;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOverviewServiceImpl implements AdminOverviewService {
    private static final int MAX_PERIOD_DAYS = 90;

    private final UserAccountRepository users;
    private final InterviewSessionRepository sessions;
    private final InterviewTemplateRepository templates;
    private final Clock clock;

    @Override
    public AdminOverviewResponse get(int days) {
        if (days < 1 || days > MAX_PERIOD_DAYS) {
            throw new DomainException(
                    ErrorCode.VALIDATION_FAILED,
                    "days must be between 1 and " + MAX_PERIOD_DAYS);
        }

        Instant now = clock.instant();
        Instant periodStart = now.minus(days, ChronoUnit.DAYS);
        Map<InterviewSessionStatus, Long> statusCounts = new EnumMap<>(
                InterviewSessionStatus.class);
        sessions.countStatusesCreatedAfter(periodStart).forEach(item ->
                statusCounts.put(item.getStatus(), item.getTotal()));

        long completed = count(statusCounts, InterviewSessionStatus.COMPLETED);
        long totalSessions = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        return new AdminOverviewResponse(
                now,
                days,
                new AdminOverviewResponse.UserMetrics(
                        users.count(),
                        users.countByEnabledTrue(),
                        users.countByCreatedAtGreaterThanEqual(periodStart)),
                new AdminOverviewResponse.SessionMetrics(
                        totalSessions,
                        completed,
                        count(statusCounts, InterviewSessionStatus.IN_PROGRESS),
                        count(statusCounts, InterviewSessionStatus.PREPARATION_FAILED),
                        count(statusCounts, InterviewSessionStatus.SCORING_FAILED),
                        completionRate(completed, totalSessions)),
                new AdminOverviewResponse.TemplateMetrics(
                        templates.countByPublishedAtIsNotNullAndArchivedAtIsNull()));
    }

    private long count(
            Map<InterviewSessionStatus, Long> statusCounts,
            InterviewSessionStatus status) {
        return statusCounts.getOrDefault(status, 0L);
    }

    private BigDecimal completionRate(long completed, long total) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
