package com.baseProject.myBaseProject.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionExpiryService;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionExpiryService.ExpiryOutcome;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class InterviewSessionExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-08-25T08:00:00Z");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private InterviewSessionExpiryService expiryService;

    @Test
    void expiryScansConfiguredCutoffAndContinuesAfterOneSessionFails() {
        InterviewSessionExpiryJob job = job(true);
        when(sessionRepository.findExpiredIds(
                        org.mockito.ArgumentMatchers.anyCollection(),
                        org.mockito.ArgumentMatchers.eq(CUTOFF),
                        org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(10L, 11L, 12L));
        when(expiryService.expireIfInactive(10L, CUTOFF))
                .thenReturn(ExpiryOutcome.SCORING_DISPATCHED);
        when(expiryService.expireIfInactive(11L, CUTOFF))
                .thenThrow(new IllegalStateException("simulated race"));
        when(expiryService.expireIfInactive(12L, CUTOFF))
                .thenReturn(ExpiryOutcome.COMPLETED_WITHOUT_EVIDENCE);

        job.expireInactiveSessions();

        verify(expiryService).expireIfInactive(10L, CUTOFF);
        verify(expiryService).expireIfInactive(11L, CUTOFF);
        verify(expiryService).expireIfInactive(12L, CUTOFF);
        verify(sessionRepository).findExpiredIds(
                org.mockito.ArgumentMatchers.argThat(statuses -> statuses.size() == 3
                        && statuses.contains(SessionStatus.READY)
                        && statuses.contains(SessionStatus.IN_PROGRESS)
                        && statuses.contains(SessionStatus.PAUSED)),
                org.mockito.ArgumentMatchers.eq(CUTOFF),
                org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 50)));
    }

    @Test
    void disabledInterviewEngineDoesNotScan() {
        job(false).expireInactiveSessions();

        verifyNoInteractions(sessionRepository, expiryService);
    }

    private InterviewSessionExpiryJob job(boolean enabled) {
        return new InterviewSessionExpiryJob(
                new InterviewProperties(enabled, 24, 90, 5, 10_000),
                sessionRepository,
                expiryService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
