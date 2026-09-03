package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewRubricResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.dto.interview.SubmitTextAnswerRequest;
import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.RubricVersion;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.SessionListScope;
import com.baseProject.myBaseProject.enums.SessionFailureStage;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.exception.IdempotencyKeyRequiredException;
import com.baseProject.myBaseProject.exception.IdempotencyKeyReusedException;
import com.baseProject.myBaseProject.exception.InterviewAiUnavailableException;
import com.baseProject.myBaseProject.exception.AnswerRequiredException;
import com.baseProject.myBaseProject.exception.AnswerTooLongException;
import com.baseProject.myBaseProject.exception.ClientTurnIdReusedException;
import com.baseProject.myBaseProject.exception.CurrentPromptMismatchException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotConfirmedException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotFoundException;
import com.baseProject.myBaseProject.exception.ProfileNotConfirmedException;
import com.baseProject.myBaseProject.exception.ProfileNotFoundException;
import com.baseProject.myBaseProject.exception.SessionLimitReachedException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.interview.InterviewNextTurnWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.InterviewScriptWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.NewInterviewSession;
import com.baseProject.myBaseProject.interview.ProfileSnapshotFactory;
import com.baseProject.myBaseProject.interview.SessionEvent;
import com.baseProject.myBaseProject.interview.SessionProcessingClaimService;
import com.baseProject.myBaseProject.interview.SessionStateMachine;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.mapper.RubricMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionTurnRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.repository.projection.SessionSummaryProjection;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import com.baseProject.myBaseProject.service.RubricService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl implements InterviewSessionService {

    private static final String SNAPSHOT_SCHEMA_VERSION = "v1";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final String CREATE_TRANSITION_REASON = "Session accepted for script generation";
    private static final String RETRY_TRANSITION_REASON = "User retried interview AI workflow";
    private static final String START_TRANSITION_REASON = "User started interview";
    private static final String PAUSE_TRANSITION_REASON = "User paused interview";
    private static final String RESUME_TRANSITION_REASON = "User resumed interview";
    private static final Set<SessionStatus> ACTIVE_STATUSES = Set.copyOf(EnumSet.of(
            SessionStatus.CREATED,
            SessionStatus.SCRIPT_GENERATING,
            SessionStatus.READY,
            SessionStatus.IN_PROGRESS,
            SessionStatus.PAUSED,
            SessionStatus.SCORING,
            SessionStatus.FAILED));
    private static final Set<SessionStatus> HISTORY_STATUSES = Set.of(
            SessionStatus.COMPLETED,
            SessionStatus.ABANDONED);

    private final InterviewProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final CandidateProfileRepository profileRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final ProfileEducationRepository educationRepository;
    private final ProfileSkillRepository skillRepository;
    private final ProfileProjectRepository projectRepository;
    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionTurnRepository turnRepository;
    private final RubricService rubricService;
    private final ProfileSnapshotFactory snapshotFactory;
    private final SessionStateMachine stateMachine;
    private final SessionProcessingClaimService claimService;
    private final InterviewScriptWorkflowDispatcher workflowDispatcher;
    private final InterviewNextTurnWorkflowDispatcher nextTurnWorkflowDispatcher;
    private final InterviewSessionMapper sessionMapper;
    private final RubricMapper rubricMapper;
    private final Clock clock;

    @Override
    @Transactional
    public InterviewSessionAcceptedResponse create(
            Long userId,
            String idempotencyKey,
            CreateInterviewSessionRequest request) {
        ensureEnabled();
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String languageCode = request.languageCode().strip().toLowerCase(Locale.ROOT);
        String requestHash = requestHash(request, languageCode);

        // get user id for update
        UserAccount user = userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists, userId=" + userId));

        InterviewSession existing = sessionRepository
                .findByUserIdAndCreationKey(userId, normalizedKey)
                .orElse(null);

        if (existing != null) {
            if (!existing.getCreationRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }

            return sessionMapper.toAcceptedResponse(existing);
        }

        // check profile and job description must be belong to user
        CandidateProfile profile = profileRepository.findActiveOwnedByIdForUpdate(
                        request.profileId(), userId)
                .orElseThrow(ProfileNotFoundException::new);
        if (!profile.isConfirmed()) {
            throw new ProfileNotConfirmedException();
        }

        JobDescription jobDescription = jobDescriptionRepository.findActiveOwnedByIdForUpdate(
                        request.jobDescriptionId(), userId)
                .orElseThrow(JobDescriptionNotFoundException::new);
        if (jobDescription.getStatus() != JobDescriptionStatus.READY) {
            throw new JobDescriptionNotConfirmedException();
        }

        ensureCapacity(userId);
        RubricVersion rubricVersion = rubricService.getCurrentPublishedVersion();
        NewInterviewSession command = new NewInterviewSession(
                user,
                profile,
                jobDescription,
                rubricVersion,
                normalizedKey,
                requestHash,
                request.difficulty(),
                request.mode(),
                languageCode,
                UUID.randomUUID(),
                SNAPSHOT_SCHEMA_VERSION,
                snapshotFactory.create(
                        profile,
                        educationRepository.findByProfileIdOrderByDisplayOrderAsc(profile.getId()),
                        skillRepository.findByProfileIdOrderByDisplayOrderAsc(profile.getId()),
                        projectRepository.findByProfileIdOrderByDisplayOrderAsc(profile.getId())));

        InterviewSession created = stateMachine.create(command);
        InterviewSession generating = stateMachine.transitionSystem(
                created.getId(),
                created.getVersion(),
                SessionEvent.DISPATCH_SCRIPT_GENERATION,
                null,
                null,
                null,
                CREATE_TRANSITION_REASON);

        UUID processingToken = UUID.randomUUID();
        boolean claimed = claimService.claim(
                generating.getId(),
                SessionProcessingStage.SCRIPT_GENERATION,
                processingToken,
                properties.processingLease());
        if (!claimed) {
            throw new IllegalStateException(
                    "New script generation work could not be claimed, sessionId="
                            + generating.getId());
        }

        InterviewSession claimedSession = sessionRepository.findByIdAndUserId(
                        generating.getId(), userId)
                .orElseThrow(SessionNotFoundException::new);
        workflowDispatcher.dispatchAfterCommit(claimedSession.getId(), processingToken);
        return sessionMapper.toAcceptedResponse(claimedSession);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewSessionResponse get(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        return sessionMapper.toResponse(session, turnRepository.findOwnedHistory(sessionId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InterviewSessionSummaryResponse> list(
            Long userId,
            SessionListScope scope,
            int page,
            int size) {
        Collection<SessionStatus> statuses = statusesFor(scope);
        PageRequest pageable = PageRequest.of(page, size, sortFor(scope));
        Page<SessionSummaryProjection> summaries = sessionRepository
                .findSummariesByUserIdAndStatuses(userId, statuses, pageable);
        return PageResponse.from(summaries.map(sessionMapper::toSummaryResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewRubricResponse getRubric(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findOwnedWithLockedRubric(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getTotalQuestionCount() == 0
                || session.getStatus() == SessionStatus.CREATED
                || session.getStatus() == SessionStatus.SCRIPT_GENERATING) {
            throw new SessionInvalidStateException();
        }
        return rubricMapper.toResponse(session.getRubricVersion());
    }

    @Override
    @Transactional
    public InterviewSessionResponse start(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        InterviewSession started = stateMachine.transitionUser(
                userId,
                sessionId,
                request.expectedVersion(),
                SessionEvent.START,
                null,
                START_TRANSITION_REASON);
        SessionQuestion firstQuestion = questionRepository.findBySessionIdAndOrdinal(
                        sessionId, (short) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "READY session has no first question, sessionId=" + sessionId));
        int turnIndex = started.beginAtQuestion(firstQuestion.getOrdinal());
        SessionTurn firstPrompt = turnRepository.save(SessionTurn.firstInterviewerPrompt(
                started,
                firstQuestion,
                turnIndex,
                started.getStartedAt()));
        sessionRepository.saveAndFlush(started);
        return sessionMapper.toResponse(started, List.of(firstPrompt));
    }

    @Override
    @Transactional
    public InterviewSessionResponse pause(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        InterviewSession paused = stateMachine.transitionUser(
                userId,
                sessionId,
                request.expectedVersion(),
                SessionEvent.PAUSE,
                null,
                PAUSE_TRANSITION_REASON);
        return sessionMapper.toResponse(
                paused,
                turnRepository.findOwnedHistory(sessionId, userId));
    }

    @Override
    @Transactional
    public InterviewSessionResponse resume(
            Long userId,
            Long sessionId,
            SessionVersionRequest request) {
        AwaitingAction restoredAction = resolveAwaitingAction(userId, sessionId);
        InterviewSession resumed = stateMachine.transitionUser(
                userId,
                sessionId,
                request.expectedVersion(),
                SessionEvent.RESUME,
                restoredAction,
                RESUME_TRANSITION_REASON);
        return sessionMapper.toResponse(
                resumed,
                turnRepository.findOwnedHistory(sessionId, userId));
    }

    @Override
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
        nextTurnWorkflowDispatcher.dispatchAfterCommit(sessionId, processingToken);
        return toTextAnswerAccepted(session, candidateTurn);
    }

    @Override
    @Transactional
    public InterviewSessionAcceptedResponse retry(
            Long userId,
            Long sessionId,
            RetryInterviewSessionRequest request) {
        ensureEnabled();
        InterviewSession retrying = stateMachine.retryUserWorkflow(
                userId,
                sessionId,
                request.expectedVersion(),
                Set.of(
                        SessionFailureStage.SCRIPT_GENERATION,
                        SessionFailureStage.NEXT_TURN),
                RETRY_TRANSITION_REASON);
        UUID processingToken = UUID.randomUUID();
        SessionProcessingStage processingStage = retrying.getProcessingStage();
        boolean claimed = claimService.claim(
                retrying.getId(),
                processingStage,
                processingToken,
                properties.processingLease());
        if (!claimed) {
            throw new IllegalStateException(
                    "Retried interview work could not be claimed, sessionId="
                            + retrying.getId());
        }

        InterviewSession claimedSession = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        if (processingStage == SessionProcessingStage.SCRIPT_GENERATION) {
            workflowDispatcher.dispatchAfterCommit(sessionId, processingToken);
        } else if (processingStage == SessionProcessingStage.NEXT_TURN) {
            nextTurnWorkflowDispatcher.dispatchAfterCommit(sessionId, processingToken);
        } else {
            throw new IllegalStateException(
                    "Unsupported retried processing stage " + processingStage);
        }
        return sessionMapper.toAcceptedResponse(claimedSession);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new InterviewAiUnavailableException();
        }
    }

    private void ensureCapacity(Long userId) {
        if (sessionRepository.countByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                >= properties.maxActivePerUser()) {
            throw new SessionLimitReachedException(properties.maxActivePerUser());
        }
    }

    private Collection<SessionStatus> statusesFor(SessionListScope scope) {
        return switch (scope) {
            case ACTIVE -> ACTIVE_STATUSES;
            case HISTORY -> HISTORY_STATUSES;
            case ALL -> EnumSet.allOf(SessionStatus.class);
        };
    }

    private Sort sortFor(SessionListScope scope) {
        return switch (scope) {
            case ACTIVE, ALL -> Sort.by(
                    Sort.Order.desc("lastActivityAt"),
                    Sort.Order.desc("id"));
            case HISTORY -> Sort.by(
                    Sort.Order.desc("completedAt"),
                    Sort.Order.desc("updatedAt"),
                    Sort.Order.desc("id"));
        };
    }

    private AwaitingAction resolveAwaitingAction(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.PAUSED) {
            throw new SessionInvalidStateException();
        }
        SessionTurn latestTurn = turnRepository
                .findFirstBySessionIdAndSessionUserIdOrderByTurnIndexDesc(sessionId, userId)
                .orElseThrow(SessionInvalidStateException::new);
        if (latestTurn.getRole() != TurnRole.INTERVIEWER) {
            throw new SessionInvalidStateException();
        }
        return AwaitingAction.CANDIDATE_ANSWER;
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
            nextTurnWorkflowDispatcher.dispatchAfterCommit(
                    session.getId(),
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

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException();
        }
        String normalized = idempotencyKey.strip();
        if (normalized.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IdempotencyKeyRequiredException();
        }
        return normalized;
    }

    private String requestHash(
            CreateInterviewSessionRequest request,
            String normalizedLanguageCode) {
        String canonical = "profileId=%d\njobDescriptionId=%d\ndifficulty=%s\nmode=%s\nlanguageCode=%s"
                .formatted(
                        request.profileId(),
                        request.jobDescriptionId(),
                        request.difficulty(),
                        request.mode(),
                        normalizedLanguageCode);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but not available", exception);
        }
    }
}
