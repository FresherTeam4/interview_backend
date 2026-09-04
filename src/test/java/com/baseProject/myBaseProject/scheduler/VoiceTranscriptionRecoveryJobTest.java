package com.baseProject.myBaseProject.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionDispatcher;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

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
class VoiceTranscriptionRecoveryJobTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private VoiceAnswerAttemptRepository attemptRepository;
    @Mock
    private VoiceTranscriptionDispatcher dispatcher;

    @Test
    void recoveryClaimsRecordedAndStaleTranscribingAttempts() {
        when(attemptRepository.findRecoverableTranscriptionIds(
                        NOW,
                        NOW.minusSeconds(30),
                        PageRequest.of(0, 50)))
                .thenReturn(List.of(301L, 302L));
        when(dispatcher.claimAndDispatch(301L)).thenReturn(true);
        when(dispatcher.claimAndDispatch(302L)).thenReturn(false);

        job(true).recoverPeriodically();

        verify(dispatcher).claimAndDispatch(301L);
        verify(dispatcher).claimAndDispatch(302L);
    }

    @Test
    void disabledTranscriptionDoesNotScanDatabase() {
        job(false).recoverPeriodically();

        verifyNoInteractions(attemptRepository);
        verify(dispatcher, never()).claimAndDispatch(org.mockito.ArgumentMatchers.anyLong());
    }

    private VoiceTranscriptionRecoveryJob job(boolean enabled) {
        VoiceProperties properties = new VoiceProperties(
                15_728_640,
                300_000,
                30,
                enabled,
                "gemini-test",
                "v1",
                7_000,
                30);
        return new VoiceTranscriptionRecoveryJob(
                properties,
                attemptRepository,
                dispatcher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
