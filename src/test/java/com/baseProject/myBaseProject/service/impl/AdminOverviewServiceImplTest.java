package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTemplateRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.projection.InterviewSessionStatusCount;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminOverviewServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Test
    void calculatesMetricsForRequestedPeriod() {
        UserAccountRepository users = mock(UserAccountRepository.class);
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewTemplateRepository templates = mock(InterviewTemplateRepository.class);
        when(users.count()).thenReturn(12L);
        when(users.countByEnabledTrue()).thenReturn(10L);
        when(users.countByCreatedAtGreaterThanEqual(any())).thenReturn(3L);
        List<InterviewSessionStatusCount> statusCounts = List.of(
                count(InterviewSessionStatus.COMPLETED, 5),
                count(InterviewSessionStatus.IN_PROGRESS, 1),
                count(InterviewSessionStatus.PREPARATION_FAILED, 1),
                count(InterviewSessionStatus.SCORING_FAILED, 1));
        when(sessions.countStatusesCreatedAfter(any())).thenReturn(statusCounts);
        when(templates.countByPublishedAtIsNotNullAndArchivedAtIsNull()).thenReturn(4L);
        AdminOverviewServiceImpl service = new AdminOverviewServiceImpl(
                users, sessions, templates, Clock.fixed(NOW, ZoneOffset.UTC));

        var response = service.get(7);

        assertThat(response.users().total()).isEqualTo(12);
        assertThat(response.sessions().totalInPeriod()).isEqualTo(8);
        assertThat(response.sessions().completionRate()).isEqualByComparingTo("62.50");
        assertThat(response.templates().published()).isEqualTo(4);
    }

    private InterviewSessionStatusCount count(
            InterviewSessionStatus status, long total) {
        InterviewSessionStatusCount value = mock(InterviewSessionStatusCount.class);
        when(value.getStatus()).thenReturn(status);
        when(value.getTotal()).thenReturn(total);
        return value;
    }
}
