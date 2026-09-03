package com.baseProject.myBaseProject.interview.turn;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.SubmitTextAnswerRequest;
import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.AnswerRequiredException;
import com.baseProject.myBaseProject.exception.AnswerTooLongException;
import com.baseProject.myBaseProject.exception.ClientTurnIdReusedException;
import com.baseProject.myBaseProject.exception.CurrentPromptMismatchException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewAnswerService {

    private final InterviewProperties properties;
    private final InterviewSessionRepository sessionRepository;
    private final SessionTurnRepository turnRepository;
    private final InterviewWorkflowDispatcher workflowDispatcher;
    private final Clock clock;

    @Transactional
    public TextAnswerAcceptedResponse submitTextAnswer(
            Long userId,
            Long sessionId,
            SubmitTextAnswerRequest request) {
        String content = normalizeAnswer(request.content());
        String clientTurnId = normalizeClientTurnId(request.clientTurnId());
        InterviewSession session = sessionRepository
                .findOwnedByIdForUpdate(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);

        SessionTurn existing = turnRepository.findBySessionIdAndClientTurnId(
                        sessionId,
                        clientTurnId)
                .orElse(null);
        if (existing != null) {
            ensureSameAnswerRequest(existing, sessionId, request.promptTurnId(), content);
            redispatchPendingNextTurn(session);
            return toTextAnswerAccepted(session, existing);
        }

        verifyVersion(session, request.expectedVersion());
        if (session.getStatus() != SessionStatus.IN_PROGRESS
                || session.getAwaitingAction() != AwaitingAction.CANDIDATE_ANSWER) {
            throw new SessionInvalidStateException();
        }

        SessionTurn currentPrompt = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(sessionId, userId)
                .orElseThrow(CurrentPromptMismatchException::new);
        if (!Objects.equals(currentPrompt.getId(), request.promptTurnId())
                || currentPrompt.getRole() != TurnRole.INTERVIEWER
                || currentPrompt.getQuestion() == null
                || session.getCurrentQuestionOrdinal() == null
                || currentPrompt.getTurnIndex() != session.getNextTurnIndex() - 1
                || (currentPrompt.isFollowUp()
                        && currentPrompt.getFollowUpDepth()
                                != session.getCurrentFollowupDepth())
                || (!currentPrompt.isFollowUp()
                        && session.getCurrentFollowupDepth() != 0)
                || currentPrompt.getQuestion().getOrdinal()
                        != session.getCurrentQuestionOrdinal()) {
            throw new CurrentPromptMismatchException();
        }

        Instant now = clock.instant();
        UUID processingToken = UUID.randomUUID();
        int turnIndex = currentPrompt.isFollowUp()
                ? session.acceptFollowUpAnswer(processingToken, now)
                : session.acceptBaseQuestionAnswer(processingToken, now);
        SessionTurn candidateTurn = turnRepository.save(SessionTurn.candidateTextAnswer(
                session,
                currentPrompt.getQuestion(),
                turnIndex,
                content,
                clientTurnId,
                now));
        sessionRepository.saveAndFlush(session);
        workflowDispatcher.dispatchAfterCommit(
                sessionId,
                SessionProcessingStage.NEXT_TURN,
                processingToken);
        return toTextAnswerAccepted(session, candidateTurn);
    }

    private String normalizeAnswer(String content) {
        if (content == null || content.isBlank()) {
            throw new AnswerRequiredException();
        }
        String normalized = content.strip();
        if (normalized.codePointCount(0, normalized.length()) > properties.maxAnswerChars()) {
            throw new AnswerTooLongException(properties.maxAnswerChars());
        }
        return normalized;
    }

    private String normalizeClientTurnId(String clientTurnId) {
        if (clientTurnId == null || clientTurnId.isBlank()) {
            throw new IllegalArgumentException("clientTurnId must not be blank");
        }
        String normalized = clientTurnId.strip();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("clientTurnId must not exceed 64 characters");
        }
        return normalized;
    }

    private void ensureSameAnswerRequest(
            SessionTurn existing,
            Long sessionId,
            Long promptTurnId,
            String content) {
        SessionTurn originalPrompt = turnRepository.findWithQuestionByIdAndSessionId(
                        promptTurnId,
                        sessionId)
                .orElse(null);
        if (!existing.getContentText().equals(content)
                || existing.getRole() != TurnRole.CANDIDATE
                || existing.getQuestion() == null
                || originalPrompt == null
                || originalPrompt.getRole() != TurnRole.INTERVIEWER
                || originalPrompt.getQuestion() == null
                || !Objects.equals(
                        existing.getQuestion().getId(),
                        originalPrompt.getQuestion().getId())
                || existing.getTurnIndex() != originalPrompt.getTurnIndex() + 1) {
            throw new ClientTurnIdReusedException();
        }
    }

    private void redispatchPendingNextTurn(InterviewSession session) {
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
    }

    private TextAnswerAcceptedResponse toTextAnswerAccepted(
            InterviewSession session,
            SessionTurn candidateTurn) {
        return new TextAnswerAcceptedResponse(
                session.getId(),
                candidateTurn.getId(),
                session.getStatus(),
                session.getAwaitingAction(),
                session.getVersion());
    }

    private void verifyVersion(InterviewSession session, long expectedVersion) {
        if (expectedVersion < 0 || session.getVersion() != expectedVersion) {
            throw new SessionVersionConflictException();
        }
    }
}
