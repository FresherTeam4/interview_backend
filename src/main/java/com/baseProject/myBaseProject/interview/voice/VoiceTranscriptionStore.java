package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.exception.SpeechTranscriptionException;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionOutcome;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VoiceTranscriptionStore {

    private static final short MAX_ATTEMPTS = 2;

    private final InterviewSessionRepository sessionRepository;
    private final VoiceAnswerAttemptRepository attemptRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TranscriptionWork prepare(Long attemptId, UUID processingToken) {
        return attemptRepository.findClaimedForTranscription(
                        attemptId,
                        processingToken.toString())
                .map(attempt -> new TranscriptionWork(
                        attempt.getId(),
                        attempt.getSession().getId(),
                        attempt.getStorageKey(),
                        attempt.getContentType(),
                        attempt.getSession().getLanguageCode()))
                .orElse(null);
    }

    @Transactional
    public boolean complete(
            TranscriptionWork work,
            UUID processingToken,
            TranscriptionOutcome outcome) {
        sessionRepository.findByIdForUpdate(work.sessionId()).orElse(null);
        VoiceAnswerAttempt attempt = attemptRepository.findWithSessionByIdForUpdate(work.attemptId())
                .orElse(null);
        if (!ownsClaim(attempt, processingToken)) {
            return false;
        }

        List<VoiceAnswerAttempt> promptAttempts = attemptRepository.findPromptAttemptsForUpdate(
                work.sessionId(),
                attempt.getPromptTurn().getId());
        Instant now = clock.instant();
        attempt.completeTranscription(
                outcome.rawText(),
                outcome.provider(),
                outcome.confidence(),
                now);

        boolean superseded = promptAttempts.stream()
                .anyMatch(candidate -> candidate.getAttemptNo() > attempt.getAttemptNo()
                        && (candidate.getStatus() == VoiceAttemptStatus.TRANSCRIBED
                                || candidate.getStatus() == VoiceAttemptStatus.CONFIRMED));
        if (superseded) {
            attempt.discard();
        } else {
            promptAttempts.stream()
                    .filter(candidate -> !candidate.getId().equals(attempt.getId()))
                    .filter(candidate -> candidate.getAttemptNo() < attempt.getAttemptNo())
                    .filter(candidate -> candidate.getStatus() == VoiceAttemptStatus.TRANSCRIBED)
                    .forEach(VoiceAnswerAttempt::discard);
        }
        attemptRepository.saveAll(promptAttempts);
        attemptRepository.flush();
        return true;
    }

    @Transactional
    public FailureOutcome fail(
            TranscriptionWork work,
            UUID processingToken,
            SpeechTranscriptionException failure) {
        sessionRepository.findByIdForUpdate(work.sessionId()).orElse(null);
        VoiceAnswerAttempt attempt = attemptRepository.findWithSessionByIdForUpdate(work.attemptId())
                .orElse(null);
        if (!ownsClaim(attempt, processingToken)) {
            return FailureOutcome.lostClaim();
        }

        if (failure.isRetryable() && attempt.getProcessingAttempts() < MAX_ATTEMPTS) {
            Instant retryAt = clock.instant().plus(1, ChronoUnit.SECONDS);
            attempt.releaseTranscriptionForRetry(retryAt, failure.getStatusMessage());
            attemptRepository.saveAndFlush(attempt);
            return FailureOutcome.retryAt(retryAt);
        }

        attempt.failTranscription(failure.getStatusMessage());
        attemptRepository.saveAndFlush(attempt);
        return FailureOutcome.terminalFailure();
    }

    private boolean ownsClaim(VoiceAnswerAttempt attempt, UUID processingToken) {
        return attempt != null
                && attempt.getStatus() == VoiceAttemptStatus.TRANSCRIBING
                && processingToken.toString().equals(attempt.getProcessingToken());
    }

    public record TranscriptionWork(
            Long attemptId,
            Long sessionId,
            String storageKey,
            String contentType,
            String languageCode) {
    }

    public record FailureOutcome(Instant retryAt, boolean failed, boolean ignored) {

        public static FailureOutcome retryAt(Instant retryAt) {
            return new FailureOutcome(retryAt, false, false);
        }

        public static FailureOutcome terminalFailure() {
            return new FailureOutcome(null, true, false);
        }

        public static FailureOutcome lostClaim() {
            return new FailureOutcome(null, false, true);
        }
    }
}
