package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.exception.ClientAttemptIdReusedException;
import com.baseProject.myBaseProject.exception.ClientTurnIdReusedException;
import com.baseProject.myBaseProject.exception.CurrentPromptMismatchException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.exception.TranscriptRequiredException;
import com.baseProject.myBaseProject.exception.VoiceAttemptInvalidStateException;
import com.baseProject.myBaseProject.exception.VoiceAttemptNotFoundException;
import com.baseProject.myBaseProject.exception.VoiceAttemptVersionConflictException;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VoiceAttemptStore {

    private final InterviewSessionRepository sessionRepository;
    private final SessionTurnRepository turnRepository;
    private final VoiceAnswerAttemptRepository attemptRepository;
    private final InterviewWorkflowDispatcher workflowDispatcher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<VoiceAnswerAttempt> findReplayOrValidate(
            Long userId,
            Long sessionId,
            VoiceAttemptDraft draft) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        VoiceAnswerAttempt existing = attemptRepository
                .findBySessionIdAndClientAttemptId(sessionId, draft.clientAttemptId())
                .orElse(null);
        if (existing != null) {
            ensureSameRequest(existing, draft);
            return Optional.of(existing);
        }
        validateNewAttempt(session, userId, sessionId, draft);
        return Optional.empty();
    }

    @Transactional
    public PersistResult persist(
            Long userId,
            Long sessionId,
            VoiceAttemptDraft draft) {
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        VoiceAnswerAttempt existing = attemptRepository
                .findBySessionIdAndClientAttemptId(sessionId, draft.clientAttemptId())
                .orElse(null);
        if (existing != null) {
            ensureSameRequest(existing, draft);
            return new PersistResult(existing, false);
        }

        SessionTurn currentPrompt = validateNewAttempt(session, userId, sessionId, draft);
        Short latestAttemptNo = attemptRepository.findMaxAttemptNo(
                sessionId,
                currentPrompt.getQuestion().getId());
        int nextAttemptNo = latestAttemptNo == null ? 1 : latestAttemptNo + 1;
        if (nextAttemptNo > Short.MAX_VALUE) {
            throw new IllegalStateException(
                    "Voice attempt number exhausted, sessionId=" + sessionId);
        }

        Instant now = clock.instant();
        VoiceAnswerAttempt attempt = attemptRepository.save(VoiceAnswerAttempt.recorded(
                session,
                currentPrompt,
                (short) nextAttemptNo,
                draft.clientAttemptId(),
                draft.storageKey(),
                draft.format(),
                draft.fileSizeBytes(),
                draft.durationMs(),
                draft.checksumSha256(),
                now));
        session.recordVoiceAttempt(now);
        sessionRepository.saveAndFlush(session);
        return new PersistResult(attempt, true);
    }

    @Transactional
    public VoiceAnswerAttempt editTranscript(
            Long userId,
            Long sessionId,
            Long attemptId,
            String editedText,
            long expectedAttemptVersion) {
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        VoiceAnswerAttempt attempt = attemptRepository.findOwnedByIdForUpdate(
                        attemptId,
                        sessionId,
                        userId)
                .orElseThrow(VoiceAttemptNotFoundException::new);
        if (attempt.getVersion() != expectedAttemptVersion) {
            throw new VoiceAttemptVersionConflictException();
        }
        if (attempt.getStatus() != VoiceAttemptStatus.TRANSCRIBED
                || session.getStatus() != SessionStatus.IN_PROGRESS
                || session.getAwaitingAction() != AwaitingAction.TRANSCRIPT_CONFIRMATION) {
            throw new VoiceAttemptInvalidStateException();
        }
        SessionTurn currentPrompt = currentPrompt(session, userId, sessionId);
        if (!Objects.equals(attempt.getPromptTurn().getId(), currentPrompt.getId())) {
            throw new CurrentPromptMismatchException();
        }

        String normalized = normalizeTranscript(editedText);
        Instant now = clock.instant();
        attempt.editTranscript(normalized);
        session.recordTranscriptEdit(now);
        attemptRepository.save(attempt);
        sessionRepository.saveAndFlush(session);
        return attempt;
    }

    @Transactional
    public TextAnswerAcceptedResponse confirm(
            Long userId,
            Long sessionId,
            Long attemptId,
            String clientTurnId,
            long expectedSessionVersion,
            long expectedAttemptVersion) {
        String normalizedClientTurnId = normalizeClientTurnId(clientTurnId);
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        VoiceAnswerAttempt attempt = attemptRepository.findOwnedByIdForUpdate(
                        attemptId,
                        sessionId,
                        userId)
                .orElseThrow(VoiceAttemptNotFoundException::new);

        if (attempt.getStatus() == VoiceAttemptStatus.CONFIRMED) {
            return replayConfirmed(session, attempt, normalizedClientTurnId);
        }
        if (session.getVersion() != expectedSessionVersion) {
            throw new SessionVersionConflictException();
        }
        if (attempt.getVersion() != expectedAttemptVersion) {
            throw new VoiceAttemptVersionConflictException();
        }
        if (attempt.getStatus() != VoiceAttemptStatus.TRANSCRIBED
                || session.getMode() != SessionMode.VOICE_TURN_BASED
                || session.getStatus() != SessionStatus.IN_PROGRESS
                || session.getAwaitingAction() != AwaitingAction.TRANSCRIPT_CONFIRMATION) {
            throw new VoiceAttemptInvalidStateException();
        }

        SessionTurn currentPrompt = currentPrompt(session, userId, sessionId);
        if (!Objects.equals(attempt.getPromptTurn().getId(), currentPrompt.getId())) {
            throw new CurrentPromptMismatchException();
        }
        if (turnRepository.findBySessionIdAndClientTurnId(
                        sessionId,
                        normalizedClientTurnId)
                .isPresent()) {
            throw new ClientTurnIdReusedException();
        }

        String finalContent = normalizeTranscript(
                attempt.getEditedText() == null
                        ? attempt.getRawText()
                        : attempt.getEditedText());
        Instant now = clock.instant();
        UUID processingToken = UUID.randomUUID();
        int turnIndex = currentPrompt.isFollowUp()
                ? session.acceptFollowUpAnswer(processingToken, now)
                : session.acceptBaseQuestionAnswer(processingToken, now);
        SessionTurn candidateTurn = turnRepository.save(SessionTurn.candidateVoiceAnswer(
                session,
                currentPrompt.getQuestion(),
                turnIndex,
                finalContent,
                normalizedClientTurnId,
                now));
        attempt.confirm(candidateTurn, now);
        List<VoiceAnswerAttempt> promptAttempts = attemptRepository.findPromptAttemptsForUpdate(
                sessionId,
                currentPrompt.getId());
        promptAttempts.stream()
                .filter(candidate -> !candidate.getId().equals(attempt.getId()))
                .filter(candidate -> candidate.getStatus() != VoiceAttemptStatus.CONFIRMED)
                .forEach(VoiceAnswerAttempt::discard);
        attemptRepository.saveAll(promptAttempts);
        sessionRepository.saveAndFlush(session);
        workflowDispatcher.dispatchAfterCommit(
                sessionId,
                SessionProcessingStage.NEXT_TURN,
                processingToken);
        return accepted(session, candidateTurn);
    }

    private SessionTurn validateNewAttempt(
            InterviewSession session,
            Long userId,
            Long sessionId,
            VoiceAttemptDraft draft) {
        if (session.getVersion() != draft.expectedVersion()) {
            throw new SessionVersionConflictException();
        }
        if (session.getMode() != SessionMode.VOICE_TURN_BASED
                || session.getStatus() != SessionStatus.IN_PROGRESS
                || (session.getAwaitingAction() != AwaitingAction.CANDIDATE_ANSWER
                        && session.getAwaitingAction()
                                != AwaitingAction.TRANSCRIPT_CONFIRMATION)) {
            throw new SessionInvalidStateException();
        }

        SessionTurn currentPrompt = currentPrompt(session, userId, sessionId);
        if (!Objects.equals(currentPrompt.getId(), draft.promptTurnId())) {
            throw new CurrentPromptMismatchException();
        }
        return currentPrompt;
    }

    private SessionTurn currentPrompt(
            InterviewSession session,
            Long userId,
            Long sessionId) {
        SessionTurn currentPrompt = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(sessionId, userId)
                .orElseThrow(CurrentPromptMismatchException::new);
        if (currentPrompt.getRole() != TurnRole.INTERVIEWER
                || currentPrompt.getQuestion() == null
                || session.getCurrentQuestionOrdinal() == null
                || currentPrompt.getTurnIndex() != session.getNextTurnIndex() - 1
                || currentPrompt.getQuestion().getOrdinal()
                        != session.getCurrentQuestionOrdinal()
                || (currentPrompt.isFollowUp()
                        && currentPrompt.getFollowUpDepth()
                                != session.getCurrentFollowupDepth())
                || (!currentPrompt.isFollowUp()
                        && session.getCurrentFollowupDepth() != 0)) {
            throw new CurrentPromptMismatchException();
        }
        return currentPrompt;
    }

    private String normalizeTranscript(String value) {
        if (value == null || value.isBlank()) {
            throw new TranscriptRequiredException();
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > 10_000) {
            throw new TranscriptRequiredException();
        }
        return normalized;
    }

    private String normalizeClientTurnId(String value) {
        if (value == null || value.isBlank()) {
            throw new ClientTurnIdReusedException();
        }
        String normalized = value.strip();
        if (normalized.length() > 64) {
            throw new ClientTurnIdReusedException();
        }
        return normalized;
    }

    private TextAnswerAcceptedResponse replayConfirmed(
            InterviewSession session,
            VoiceAnswerAttempt attempt,
            String clientTurnId) {
        SessionTurn confirmedTurn = attempt.getConfirmedTurn();
        if (confirmedTurn == null
                || !clientTurnId.equals(confirmedTurn.getClientTurnId())) {
            throw new ClientTurnIdReusedException();
        }
        if (session.getStatus() == SessionStatus.IN_PROGRESS
                && (session.getAwaitingAction() == AwaitingAction.ENGINE_RESPONSE
                        || session.getAwaitingAction() == AwaitingAction.ENGINE_RETRY)
                && session.getProcessingStage() == SessionProcessingStage.NEXT_TURN
                && session.getProcessingToken() != null) {
            workflowDispatcher.dispatchAfterCommit(
                    session.getId(),
                    SessionProcessingStage.NEXT_TURN,
                    UUID.fromString(session.getProcessingToken()));
        }
        return accepted(session, confirmedTurn);
    }

    private TextAnswerAcceptedResponse accepted(
            InterviewSession session,
            SessionTurn turn) {
        return new TextAnswerAcceptedResponse(
                session.getId(),
                turn.getId(),
                session.getStatus(),
                session.getAwaitingAction(),
                session.getVersion());
    }

    private void ensureSameRequest(
            VoiceAnswerAttempt existing,
            VoiceAttemptDraft draft) {
        if (!Objects.equals(existing.getPromptTurn().getId(), draft.promptTurnId())
                || !existing.getClientAttemptId().equals(draft.clientAttemptId())
                || existing.getFormat() != draft.format()
                || existing.getFileSizeBytes() != draft.fileSizeBytes()
                || existing.getDurationMs() != draft.durationMs()
                || !existing.getChecksumSha256().equals(draft.checksumSha256())) {
            throw new ClientAttemptIdReusedException();
        }
    }

    public record VoiceAttemptDraft(
            Long promptTurnId,
            String clientAttemptId,
            long expectedVersion,
            String storageKey,
            AudioFormat format,
            long fileSizeBytes,
            int durationMs,
            String checksumSha256) {
    }

    public record PersistResult(VoiceAnswerAttempt attempt, boolean created) {
    }
}
