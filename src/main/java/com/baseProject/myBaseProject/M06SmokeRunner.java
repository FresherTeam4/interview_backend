package com.baseProject.myBaseProject;

import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.ProfileSource;
import com.baseProject.myBaseProject.enums.QuestionSourceType;
import com.baseProject.myBaseProject.enums.SessionMode;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.IdempotencyKeyRequiredException;
import com.baseProject.myBaseProject.exception.IdempotencyKeyReusedException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotConfirmedException;
import com.baseProject.myBaseProject.exception.ProfileNotConfirmedException;
import com.baseProject.myBaseProject.exception.ProfileNotFoundException;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.exception.SessionLimitReachedException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.exception.SessionRetryNotAllowedException;
import com.baseProject.myBaseProject.exception.SessionVersionConflictException;
import com.baseProject.myBaseProject.interview.InterviewScriptGenerationWorker;
import com.baseProject.myBaseProject.interview.InterviewScriptWorkCoordinator;
import com.baseProject.myBaseProject.interview.SessionProcessingClaimService;
import com.baseProject.myBaseProject.interview.ai.GeneratedQuestion;
import com.baseProject.myBaseProject.interview.ai.GeneratedScript;
import com.baseProject.myBaseProject.interview.ai.InterviewQuestionGenerator;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationOutcome;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.SessionStateTransitionRepository;
import com.baseProject.myBaseProject.service.InterviewSessionService;

import jakarta.persistence.EntityManager;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Temporary M06 verification aid. It drives create/idempotency/retry/recovery through the real
 * service beans against synthetic fixtures inside one rollback-only transaction, so the run leaves
 * no row behind and does not depend on developer data.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.m06-smoke.enabled", havingValue = "true")
class M06SmokeRunner implements ApplicationRunner {

    private static final List<SessionStatus> ACTIVE_STATUSES = List.of(
            SessionStatus.CREATED,
            SessionStatus.SCRIPT_GENERATING,
            SessionStatus.READY,
            SessionStatus.IN_PROGRESS,
            SessionStatus.PAUSED,
            SessionStatus.SCORING,
            SessionStatus.FAILED);

    private static final String JD_TEXT = """
            Tuyen Fresher Backend Developer.
            Yeu cau: Java, Spring Boot, SQL co ban, hieu REST API va Git.
            """;

    private final EntityManager entityManager;
    private final InterviewProperties properties;
    private final InterviewSessionService sessionService;
    private final InterviewSessionRepository sessionRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionStateTransitionRepository transitionRepository;
    private final SessionProcessingClaimService claimService;
    private final InterviewScriptGenerationWorker worker;
    private final InterviewScriptWorkCoordinator workCoordinator;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    M06SmokeRunner(
            EntityManager entityManager,
            InterviewProperties properties,
            InterviewSessionService sessionService,
            InterviewSessionRepository sessionRepository,
            SessionQuestionRepository questionRepository,
            SessionStateTransitionRepository transitionRepository,
            SessionProcessingClaimService claimService,
            InterviewScriptGenerationWorker worker,
            InterviewScriptWorkCoordinator workCoordinator,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.properties = properties;
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.transitionRepository = transitionRepository;
        this.claimService = claimService;
        this.worker = worker;
        this.workCoordinator = workCoordinator;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        transactionTemplate.executeWithoutResult(status -> {
            status.setRollbackOnly();
            require(properties.enabled(), "app.interview.enabled must be true for this smoke");
            Fixture fixture = createFixture();
            CreateInterviewSessionRequest request = new CreateInterviewSessionRequest(
                    fixture.profileId(),
                    fixture.jobDescriptionId(),
                    InterviewDifficulty.MEDIUM,
                    SessionMode.TEXT,
                    "vi");

            verifyValidationContract(fixture, request);
            InterviewSessionAcceptedResponse first = verifyCreateAndReplay(fixture, request);
            verifyOwnershipScopedRead(fixture, first.id());
            verifyRecoveryQueryFindsStaleClaim(first.id());
            InterviewSession firstReady = verifyProviderRetryReachesReady(first.id());
            InterviewSession secondReady = verifyUserRetryReachesReady(fixture, request);
            verifyIdempotencyConflict(fixture, request);
            String limitCheck = verifySessionLimit(fixture, request);

            log.info(
                    "M06_SMOKE acceptedStatus={}, acceptedVersion={}, replaySameId=true, "
                            + "autoRetry={}/v{}/q{}, userRetry={}/v{}/q{}, "
                            + "transitions={}/{}, limitCheck={}, rollback=true",
                    first.status(),
                    first.version(),
                    firstReady.getStatus(),
                    firstReady.getVersion(),
                    questionRepository.countBySessionId(firstReady.getId()),
                    secondReady.getStatus(),
                    secondReady.getVersion(),
                    questionRepository.countBySessionId(secondReady.getId()),
                    transitionCount(firstReady.getId(), fixture.userId()),
                    transitionCount(secondReady.getId(), fixture.userId()),
                    limitCheck);
        });
    }

    /** Rejected requests must fail before any session row is written. */
    private void verifyValidationContract(Fixture fixture, CreateInterviewSessionRequest request) {
        expect(IdempotencyKeyRequiredException.class,
                () -> sessionService.create(fixture.userId(), "   ", request),
                "Blank idempotency key was accepted");
        expect(IdempotencyKeyRequiredException.class,
                () -> sessionService.create(fixture.userId(), "k".repeat(129), request),
                "Over-long idempotency key was accepted by the service");
        expect(ProfileNotFoundException.class,
                () -> sessionService.create(
                        fixture.userId(),
                        newKey(),
                        withProfile(request, fixture.profileId() + 100_000L)),
                "Unknown profile was accepted");
        expect(ProfileNotFoundException.class,
                () -> sessionService.create(fixture.otherUserId(), newKey(), request),
                "Another user could target the owner's profile");
        expect(ProfileNotConfirmedException.class,
                () -> sessionService.create(
                        fixture.userId(),
                        newKey(),
                        withProfile(request, fixture.unconfirmedProfileId())),
                "Unconfirmed profile was accepted");
        expect(JobDescriptionNotConfirmedException.class,
                () -> sessionService.create(
                        fixture.userId(),
                        newKey(),
                        withJobDescription(request, fixture.draftJobDescriptionId())),
                "Draft job description was accepted");
        require(sessionRepository.count() == 0, "A rejected create left a session row behind");
    }

    private InterviewSessionAcceptedResponse verifyCreateAndReplay(
            Fixture fixture,
            CreateInterviewSessionRequest request) {
        String key = newKey();
        InterviewSessionAcceptedResponse first = sessionService.create(
                fixture.userId(), key, request);
        InterviewSessionAcceptedResponse replay = sessionService.create(
                fixture.userId(), " " + key + " ", request);
        require(first.id().equals(replay.id()), "Idempotent replay created a second session");
        require(first.version() == replay.version(), "Idempotent replay changed the version");
        require(first.status() == SessionStatus.SCRIPT_GENERATING,
                "Accepted session was not dispatched for script generation");
        require(sessionRepository.count() == 1, "Idempotent replay inserted an extra session");
        return first;
    }

    private void verifyOwnershipScopedRead(Fixture fixture, Long sessionId) {
        InterviewSessionResponse detail = sessionService.get(fixture.userId(), sessionId);
        require(detail.profile().id().equals(fixture.profileId())
                        && detail.jobDescription().id().equals(fixture.jobDescriptionId())
                        && detail.totalQuestionCount() == 0
                        && detail.startedAt() == null,
                "Detail response before script generation is wrong");
        expect(SessionNotFoundException.class,
                () -> sessionService.get(fixture.otherUserId(), sessionId),
                "Another user could read the session");
    }

    /** The recovery job query must see a claim whose lease has expired. */
    private void verifyRecoveryQueryFindsStaleClaim(Long sessionId) {
        Instant now = clock.instant();
        List<Long> recoverable = sessionRepository.findRecoverableWorkIds(
                List.of(SessionStatus.SCRIPT_GENERATING),
                List.of(SessionProcessingStage.SCRIPT_GENERATION),
                now,
                now.plusSeconds(60),
                PageRequest.of(0, 50));
        require(recoverable.contains(sessionId), "Recovery query missed an expired claim");
    }

    /** Attempt 1 fails with a retryable provider error; attempt 2 must produce a READY script. */
    private InterviewSession verifyProviderRetryReachesReady(Long sessionId) {
        InterviewSession claimed = requireSession(sessionId);
        UUID firstToken = UUID.fromString(claimed.getProcessingToken());
        require(worker.process(sessionId, firstToken).isPresent(),
                "First retryable failure did not schedule a retry");
        InterviewSession released = requireSession(sessionId);
        require(released.getProcessingToken() == null
                        && released.getNextRetryAt() != null
                        && released.getStatus() == SessionStatus.SCRIPT_GENERATING
                        && released.getProcessingAttempts() == 1,
                "Retryable failure did not release the claim for a later retry");
        sleepPastBackoff();
        UUID secondToken = UUID.randomUUID();
        require(claimService.claim(
                        sessionId,
                        SessionProcessingStage.SCRIPT_GENERATION,
                        secondToken,
                        properties.processingLease()),
                "Second provider attempt could not be claimed");
        require(worker.process(sessionId, secondToken).isEmpty(),
                "Successful retry unexpectedly requested another retry");
        InterviewSession ready = requireSession(sessionId);
        require(ready.getStatus() == SessionStatus.READY
                        && ready.getProcessingStage() == null
                        && ready.getProcessingToken() == null
                        && ready.getStatusMessage() == null
                        && ready.getTotalQuestionCount() > 0
                        && questionRepository.countBySessionId(sessionId)
                                == ready.getTotalQuestionCount(),
                "Auto-retried session did not become a complete READY script");
        return ready;
    }

    /** A non-retryable failure must land in FAILED and only recover through an explicit retry. */
    private InterviewSession verifyUserRetryReachesReady(
            Fixture fixture,
            CreateInterviewSessionRequest request) {
        InterviewSessionAcceptedResponse accepted = sessionService.create(
                fixture.userId(), newKey(), request);
        Long sessionId = accepted.id();
        InterviewSession claimed = requireSession(sessionId);
        UUID token = UUID.fromString(claimed.getProcessingToken());
        InterviewScriptWorkCoordinator.FailureOutcome failure = workCoordinator.handleFailure(
                sessionId, token, ScriptGenerationException.noApiKey());
        require(failure.failed(), "Non-retryable provider failure did not mark FAILED");
        InterviewSession failed = requireSession(sessionId);
        require(failed.getStatus() == SessionStatus.FAILED
                        && failed.getStatusMessage() != null
                        && failed.getProcessingToken() == null,
                "Session failure state was not persisted safely");

        expect(SessionVersionConflictException.class,
                () -> sessionService.retry(
                        fixture.userId(),
                        sessionId,
                        new RetryInterviewSessionRequest(failed.getVersion() - 1)),
                "Stale expectedVersion was accepted on retry");
        expect(SessionNotFoundException.class,
                () -> sessionService.retry(
                        fixture.otherUserId(),
                        sessionId,
                        new RetryInterviewSessionRequest(failed.getVersion())),
                "Another user could retry the session");

        sessionService.retry(
                fixture.userId(),
                sessionId,
                new RetryInterviewSessionRequest(failed.getVersion()));
        InterviewSession retried = requireSession(sessionId);
        require(retried.getStatus() == SessionStatus.SCRIPT_GENERATING
                        && retried.getProcessingToken() != null
                        && retried.getProcessingAttempts() == 1,
                "User retry did not reset attempts and re-claim script generation");
        require(worker.process(sessionId, UUID.fromString(retried.getProcessingToken())).isEmpty(),
                "User retry unexpectedly requested another retry");
        InterviewSession ready = requireSession(sessionId);
        require(ready.getStatus() == SessionStatus.READY,
                "User-retried session did not become READY");
        expect(SessionRetryNotAllowedException.class,
                () -> sessionService.retry(
                        fixture.userId(),
                        sessionId,
                        new RetryInterviewSessionRequest(ready.getVersion())),
                "Retry was allowed on a READY session");
        return ready;
    }

    private void verifyIdempotencyConflict(
            Fixture fixture,
            CreateInterviewSessionRequest request) {
        String key = newKey();
        sessionService.create(fixture.userId(), key, request);
        expect(IdempotencyKeyReusedException.class,
                () -> sessionService.create(
                        fixture.userId(),
                        key,
                        new CreateInterviewSessionRequest(
                                request.profileId(),
                                request.jobDescriptionId(),
                                InterviewDifficulty.HARD,
                                request.mode(),
                                request.languageCode())),
                "Reused idempotency key with a different body was accepted");
    }

    /** Only meaningful when the configured limit is at or below the sessions already open. */
    private String verifySessionLimit(Fixture fixture, CreateInterviewSessionRequest request) {
        long open = sessionRepository.countByUserIdAndStatusIn(fixture.userId(), ACTIVE_STATUSES);
        int max = properties.maxActivePerUser();
        if (open < max) {
            return "skipped(open=" + open + ",max=" + max + ")";
        }
        expect(SessionLimitReachedException.class,
                () -> sessionService.create(fixture.userId(), newKey(), request),
                "Session limit was not enforced");
        return "enforced(open=" + open + ",max=" + max + ")";
    }

    private Fixture createFixture() {
        Instant now = clock.instant();
        String tag = UUID.randomUUID().toString();
        UserAccount owner = persistUser("M06 Smoke Owner", "m06-owner-" + tag, now);
        UserAccount stranger = persistUser("M06 Smoke Stranger", "m06-stranger-" + tag, now);

        CandidateProfile profile = persistProfile(
                owner, persistCvDocument(owner, "confirmed-" + tag, now), now, now);
        entityManager.persist(ProfileSkill.builder()
                .profile(profile)
                .name("Spring Boot")
                .category("BACKEND")
                .displayOrder((short) 0)
                .build());
        entityManager.persist(ProfileProject.builder()
                .profile(profile)
                .name("Interview scheduler")
                .description("REST service for managing interview slots")
                .roleInProject("Backend developer")
                .techStack("Java, Spring Boot, MySQL")
                .displayOrder((short) 0)
                .build());
        CandidateProfile unconfirmed = persistProfile(
                owner, persistCvDocument(owner, "unconfirmed-" + tag, now), null, now);

        JobDescription ready = persistJobDescription(owner, JobDescriptionStatus.READY, now, now);
        JobDescription draft = persistJobDescription(owner, JobDescriptionStatus.DRAFT, null, now);
        entityManager.flush();
        return new Fixture(
                owner.getId(),
                stranger.getId(),
                profile.getId(),
                unconfirmed.getId(),
                ready.getId(),
                draft.getId());
    }

    /**
     * A synthetic Google identity satisfies {@code chk_user_accounts_login_method} without putting
     * any password-like literal in the repository.
     */
    private UserAccount persistUser(String fullName, String localPart, Instant now) {
        UserAccount user = UserAccount.builder()
                .fullName(fullName)
                .email(localPart + "@m06-smoke.invalid")
                .googleId("m06-smoke-" + localPart)
                .role(UserRole.USER)
                .enabled(true)
                .createdAt(now)
                .build();
        entityManager.persist(user);
        return user;
    }

    private CvDocument persistCvDocument(UserAccount user, String tag, Instant now) {
        CvDocument document = CvDocument.builder()
                .user(user)
                .storageKey("m06-smoke/" + tag + ".pdf")
                .originalFilename("m06-smoke.pdf")
                .contentType("application/pdf")
                .fileSizeBytes(4096L)
                .checksumSha256(randomChecksum())
                .status(CvDocumentStatus.PARSED)
                .active(true)
                .uploadedAt(now)
                .parsedAt(now)
                .build();
        entityManager.persist(document);
        return document;
    }

    private CandidateProfile persistProfile(
            UserAccount user,
            CvDocument document,
            Instant confirmedAt,
            Instant now) {
        CandidateProfile profile = CandidateProfile.builder()
                .user(user)
                .cvDocument(document)
                .headline("Fresher Java Developer")
                .targetPosition("Backend Developer")
                .seniorityLevel("FRESHER")
                .yearsExperience(new BigDecimal("1.0"))
                .source(ProfileSource.AUTO_PARSED)
                .confirmedAt(confirmedAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
        entityManager.persist(profile);
        return profile;
    }

    private JobDescription persistJobDescription(
            UserAccount user,
            JobDescriptionStatus status,
            Instant confirmedAt,
            Instant now) {
        JobDescription jobDescription = JobDescription.builder()
                .user(user)
                .title("Fresher Backend Developer")
                .sourceType(JobDescriptionSourceType.TEXT)
                .status(status)
                .checksumSha256(randomChecksum())
                .rawText(JD_TEXT)
                .confirmedText(JD_TEXT)
                .confirmedAt(confirmedAt)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        entityManager.persist(jobDescription);
        return jobDescription;
    }

    private CreateInterviewSessionRequest withProfile(
            CreateInterviewSessionRequest request,
            Long profileId) {
        return new CreateInterviewSessionRequest(
                profileId,
                request.jobDescriptionId(),
                request.difficulty(),
                request.mode(),
                request.languageCode());
    }

    private CreateInterviewSessionRequest withJobDescription(
            CreateInterviewSessionRequest request,
            Long jobDescriptionId) {
        return new CreateInterviewSessionRequest(
                request.profileId(),
                jobDescriptionId,
                request.difficulty(),
                request.mode(),
                request.languageCode());
    }

    private String newKey() {
        return "m06-smoke-" + UUID.randomUUID();
    }

    private String randomChecksum() {
        return UUID.randomUUID().toString().replace("-", "").repeat(2);
    }

    private int transitionCount(Long sessionId, Long userId) {
        return transitionRepository
                .findBySessionIdAndSessionUserIdOrderByOccurredAtAscIdAsc(sessionId, userId)
                .size();
    }

    /**
     * Claim and release run as bulk updates that clear the persistence context, so every assertion
     * reads the row again instead of trusting a managed entity.
     */
    private InterviewSession requireSession(Long sessionId) {
        entityManager.flush();
        entityManager.clear();
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Smoke session disappeared"));
    }

    /** The coordinator schedules a retry one second out and the claim query honours it. */
    private void sleepPastBackoff() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("M06 smoke was interrupted", exception);
        }
    }

    private void expect(
            Class<? extends RuntimeException> expected,
            Runnable action,
            String message) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            if (expected.isInstance(exception)) {
                return;
            }
            throw new IllegalStateException(
                    message + " (unexpected " + exception.getClass().getSimpleName() + ")",
                    exception);
        }
        throw new IllegalStateException(message);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Fixture(
            Long userId,
            Long otherUserId,
            Long profileId,
            Long unconfirmedProfileId,
            Long jobDescriptionId,
            Long draftJobDescriptionId) {
    }
}

/** Replaces the Gemini-backed generator: attempt 1 fails retryably, later attempts succeed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.m06-smoke.enabled", havingValue = "true")
class M06SmokeConfiguration {

    @Bean
    @Primary
    InterviewQuestionGenerator m06SmokeQuestionGenerator(InterviewAiProperties properties) {
        AtomicInteger calls = new AtomicInteger();
        return input -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                throw ScriptGenerationException.providerUnavailable("synthetic smoke", null);
            }
            return validOutcome(input, properties.scriptPromptVersion(), call);
        };
    }

    private ScriptGenerationOutcome validOutcome(
            ScriptGenerationInput input,
            String promptVersion,
            int call) {
        List<GeneratedQuestion> questions = new ArrayList<>();
        String seed = input.generationSeed().toString();
        for (int ordinal = 1; ordinal <= input.questionCount(); ordinal++) {
            questions.add(new GeneratedQuestion(
                    ordinal,
                    "M06 smoke question " + call + "-" + ordinal + " for seed " + seed,
                    "M06 workflow " + ordinal,
                    "COMMUNICATION",
                    3,
                    QuestionSourceType.GENERAL_BEHAVIORAL,
                    null,
                    null,
                    null,
                    "m06-" + seed + "-" + ordinal));
        }
        return new ScriptGenerationOutcome(
                new GeneratedScript(questions),
                "mock-m06",
                promptVersion,
                42,
                7);
    }
}
