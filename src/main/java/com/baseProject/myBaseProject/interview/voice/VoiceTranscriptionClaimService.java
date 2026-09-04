package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoiceTranscriptionClaimService {

    private final VoiceProperties properties;
    private final VoiceAnswerAttemptRepository attemptRepository;
    private final Clock clock;

    @Transactional
    public boolean claim(Long attemptId, UUID token) {
        if (!properties.transcriptionEnabled()) {
            return false;
        }
        Instant now = clock.instant();
        Instant staleBefore = now.minus(
                properties.sttProcessingLeaseSeconds(),
                ChronoUnit.SECONDS);
        return attemptRepository.claimTranscription(
                attemptId,
                token.toString(),
                now,
                staleBefore) == 1;
    }
}
