package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.ClientAttemptIdReusedException;
import com.baseProject.myBaseProject.exception.CurrentPromptMismatchException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class VoiceAttemptStore {

    private final InterviewSessionRepository sessionRepository;
    private final SessionTurnRepository turnRepository;
    private final VoiceAnswerAttemptRepository attemptRepository;
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
                || session.getAwaitingAction() != AwaitingAction.CANDIDATE_ANSWER) {
            throw new SessionInvalidStateException();
        }

        SessionTurn currentPrompt = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(
                        sessionId,
                        userId)
                .orElseThrow(CurrentPromptMismatchException::new);
        if (!Objects.equals(currentPrompt.getId(), draft.promptTurnId())
                || currentPrompt.getRole() != TurnRole.INTERVIEWER
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
