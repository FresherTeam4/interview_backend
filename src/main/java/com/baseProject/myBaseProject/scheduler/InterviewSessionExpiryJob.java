package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionExpiryService;
import com.baseProject.myBaseProject.interview.lifecycle.InterviewSessionExpiryService.ExpiryOutcome;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewSessionExpiryJob {

    private static final int EXPIRY_BATCH_SIZE = 50;
    private static final Set<SessionStatus> EXPIRABLE_STATUSES = EnumSet.of(
            SessionStatus.READY,
            SessionStatus.IN_PROGRESS,
            SessionStatus.PAUSED);

    private final InterviewProperties properties;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewSessionExpiryService expiryService;
    private final Clock clock;

    @Scheduled(cron = "${app.interview.expiry-cron}")
    public void expireInactiveSessions() {
        if (!properties.enabled()) {
            return;
        }

        Instant cutoff = clock.instant().minus(properties.inactivityTimeout());
        List<Long> sessionIds;
        try {
            sessionIds = sessionRepository.findExpiredIds(
                    EXPIRABLE_STATUSES,
                    cutoff,
                    PageRequest.of(0, EXPIRY_BATCH_SIZE));
        } catch (RuntimeException exception) {
            log.error(
                    "Interview expiry scan failed: exceptionType={}",
                    exception.getClass().getSimpleName());
            return;
        }

        int expired = 0;
        for (Long sessionId : sessionIds) {
            try {
                if (expiryService.expireIfInactive(sessionId, cutoff)
                        != ExpiryOutcome.SKIPPED) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                log.error(
                        "Interview expiry failed: sessionId={}, exceptionType={}",
                        sessionId,
                        exception.getClass().getSimpleName());
            }
        }
        if (!sessionIds.isEmpty()) {
            log.info(
                    "Interview expiry scanned: candidates={}, expired={}",
                    sessionIds.size(),
                    expired);
        }
    }
}
