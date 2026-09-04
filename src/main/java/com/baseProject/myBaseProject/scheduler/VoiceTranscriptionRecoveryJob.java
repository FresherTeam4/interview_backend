package com.baseProject.myBaseProject.scheduler;

import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionDispatcher;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceTranscriptionRecoveryJob {

    private static final int RECOVERY_BATCH_SIZE = 50;

    private final VoiceProperties properties;
    private final VoiceAnswerAttemptRepository attemptRepository;
    private final VoiceTranscriptionDispatcher dispatcher;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverWhenApplicationIsReady() {
        recover();
    }

    @Scheduled(cron = "${app.interview.recovery-cron}")
    public void recoverPeriodically() {
        recover();
    }

    private void recover() {
        if (!properties.transcriptionEnabled()) {
            return;
        }
        Instant now = clock.instant();
        try {
            List<Long> attemptIds = attemptRepository.findRecoverableTranscriptionIds(
                    now,
                    now.minus(properties.sttProcessingLeaseSeconds(), ChronoUnit.SECONDS),
                    PageRequest.of(0, RECOVERY_BATCH_SIZE));
            int claimed = 0;
            for (Long attemptId : attemptIds) {
                if (dispatcher.claimAndDispatch(attemptId)) {
                    claimed++;
                }
            }
            if (!attemptIds.isEmpty()) {
                log.info(
                        "Voice transcription recovery scanned: candidates={}, claimed={}",
                        attemptIds.size(),
                        claimed);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Voice transcription recovery failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
