package com.baseProject.myBaseProject.interview.lifecycle;

import com.baseProject.myBaseProject.config.properites.InterviewProperties;
import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.RubricVersion;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.IdempotencyKeyRequiredException;
import com.baseProject.myBaseProject.exception.IdempotencyKeyReusedException;
import com.baseProject.myBaseProject.exception.InterviewAiUnavailableException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotConfirmedException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotFoundException;
import com.baseProject.myBaseProject.exception.ProfileNotConfirmedException;
import com.baseProject.myBaseProject.exception.ProfileNotFoundException;
import com.baseProject.myBaseProject.exception.SessionLimitReachedException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.lifecycle.model.NewInterviewSession;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.interview.snapshot.ProfileSnapshotFactory;
import com.baseProject.myBaseProject.interview.workflow.InterviewWorkflowDispatcher;
import com.baseProject.myBaseProject.interview.workflow.SessionProcessingClaimService;
import com.baseProject.myBaseProject.mapper.InterviewSessionMapper;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.ProfileEducationRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.RubricService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewSessionCreationService {

    private static final String SNAPSHOT_SCHEMA_VERSION = "v1";
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    private static final String CREATE_TRANSITION_REASON = "Session accepted for script generation";
    private static final Set<SessionStatus> ACTIVE_STATUSES = Set.copyOf(EnumSet.of(
            SessionStatus.CREATED,
            SessionStatus.SCRIPT_GENERATING,
            SessionStatus.READY,
            SessionStatus.IN_PROGRESS,
            SessionStatus.PAUSED,
            SessionStatus.SCORING,
            SessionStatus.FAILED));

    private final InterviewProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final CandidateProfileRepository profileRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final ProfileEducationRepository educationRepository;
    private final ProfileSkillRepository skillRepository;
    private final ProfileProjectRepository projectRepository;
    private final InterviewSessionRepository sessionRepository;
    private final RubricService rubricService;
    private final ProfileSnapshotFactory snapshotFactory;
    private final SessionStateMachine stateMachine;
    private final SessionProcessingClaimService claimService;
    private final InterviewWorkflowDispatcher workflowDispatcher;
    private final InterviewSessionMapper sessionMapper;

    @Transactional
    public InterviewSessionAcceptedResponse create(
            Long userId,
            String idempotencyKey,
            CreateInterviewSessionRequest request) {
        ensureEnabled();
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String languageCode = request.languageCode().strip().toLowerCase(Locale.ROOT);
        String requestHash = requestHash(request, languageCode);

        // permistic lock
        UserAccount user = userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists, userId=" + userId));

        // check trùng lặp session
        InterviewSession existing = sessionRepository
                .findByUserIdAndCreationKey(userId, normalizedKey)
                .orElse(null);

        if (existing != null) {
            if (!existing.getCreationRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }

            return sessionMapper.toAcceptedResponse(existing);
        }

        // lấy data cần thiết
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

        // snapshot data
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


        InterviewSession generating = stateMachine.dispatchScriptGeneration(
                created.getId(),
                created.getVersion(),
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
        workflowDispatcher.dispatchAfterCommit(
                claimedSession.getId(),
                SessionProcessingStage.SCRIPT_GENERATION,
                processingToken);
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
