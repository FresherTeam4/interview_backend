package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewQuestionProperties;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.ai.InterviewQuestionGenerator;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationOutcome;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InterviewScriptGenerationService {

    private static final int DIVERSITY_HISTORY_SESSION_LIMIT = 3;
    private static final String TRANSITION_REASON = "Generated base-question script persisted";

    private final InterviewQuestionGenerator questionGenerator;
    private final QuestionScriptValidator validator;
    private final InterviewQuestionProperties questionProperties;
    private final InterviewAiProperties aiProperties;
    private final InterviewSessionRepository sessionRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionQuestionRepository questionRepository;
    private final CandidateProfileRepository profileRepository;
    private final ProfileProjectRepository projectRepository;
    private final ProfileSkillRepository skillRepository;
    private final SessionStateMachine stateMachine;
    private final Clock clock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public InterviewScriptGenerationService(
            InterviewQuestionGenerator questionGenerator,
            QuestionScriptValidator validator,
            InterviewQuestionProperties questionProperties,
            InterviewAiProperties aiProperties,
            InterviewSessionRepository sessionRepository,
            SessionContextSnapshotRepository snapshotRepository,
            SessionQuestionRepository questionRepository,
            CandidateProfileRepository profileRepository,
            ProfileProjectRepository projectRepository,
            ProfileSkillRepository skillRepository,
            SessionStateMachine stateMachine,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.questionGenerator = questionGenerator;
        this.validator = validator;
        this.questionProperties = questionProperties;
        this.aiProperties = aiProperties;
        this.sessionRepository = sessionRepository;
        this.snapshotRepository = snapshotRepository;
        this.questionRepository = questionRepository;
        this.profileRepository = profileRepository;
        this.projectRepository = projectRepository;
        this.skillRepository = skillRepository;
        this.stateMachine = stateMachine;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Calls the provider without a database transaction, then commits the complete script and READY
     * transition together. Only a diversity rejection receives one immediate regeneration attempt.
     */
    public ScriptGenerationResult generateAndPersist(Long sessionId, UUID processingToken) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(processingToken);

        for (int attempt = 0; attempt < 2; attempt++) {
            GenerationPreparation preparation = loadPreparation(sessionId, processingToken);
            ScriptGenerationOutcome outcome = questionGenerator.generate(preparation.input());
            validateProviderContract(outcome);

            try {
                ValidatedScript script = validator.validate(
                        outcome,
                        preparation.input(),
                        preparation.allowedProjectIds(),
                        preparation.allowedSkillIds(),
                        preparation.recentSessionIds(),
                        preparation.history());
                ScriptGenerationResult result = persist(
                        sessionId,
                        processingToken,
                        preparation.profileId(),
                        preparation.jobDescriptionHash(),
                        script,
                        attempt > 0);
                log.info(
                        "Interview script persisted: sessionId={}, questionCount={}, "
                                + "model={}, promptVersion={}, durationMs={}, diversityRetried={}",
                        sessionId,
                        result.questionCount(),
                        result.modelName(),
                        result.promptVersion(),
                        result.durationMs(),
                        result.diversityRetried());
                return result;
            } catch (ScriptGenerationException exception) {
                if (attempt == 0 && exception.isDiversityRejected()) {
                    log.info("Regenerating interview script after diversity rejection: sessionId={}",
                            sessionId);
                    continue;
                }
                throw exception;
            }
        }
        throw new IllegalStateException("Interview script generation loop ended unexpectedly");
    }

    private GenerationPreparation loadPreparation(Long sessionId, UUID processingToken) {
        return Objects.requireNonNull(readTransaction.execute(status -> {
            SessionContextSnapshot snapshot = snapshotRepository.findBySessionId(sessionId)
                    .orElseThrow(SessionNotFoundException::new);
            InterviewSession session = snapshot.getSession();
            verifyGenerationClaim(session, processingToken);
            if (questionRepository.countBySessionId(sessionId) != 0) {
                throw new SessionInvalidStateException();
            }

            Long profileId = session.getProfile().getId();
            Set<Long> snapshotProjectIds = extractIds(snapshot.getProfileJson(), "projects");
            Set<Long> snapshotSkillIds = extractIds(snapshot.getProfileJson(), "skills");
            Set<Long> persistedProjectIds = projectRepository
                    .findByProfileIdOrderByDisplayOrderAsc(profileId)
                    .stream()
                    .map(ProfileProject::getId)
                    .collect(Collectors.toSet());
            Set<Long> persistedSkillIds = skillRepository
                    .findByProfileIdOrderByDisplayOrderAsc(profileId)
                    .stream()
                    .map(ProfileSkill::getId)
                    .collect(Collectors.toSet());
            snapshotProjectIds.retainAll(persistedProjectIds);
            snapshotSkillIds.retainAll(persistedSkillIds);

            History history = loadHistory(
                    profileId,
                    snapshot.getJobDescriptionHash(),
                    sessionId);
            Set<String> excludedSignatures = history.questions().stream()
                    .map(QuestionHistoryProjection::questionSignature)
                    .collect(Collectors.toUnmodifiableSet());
            ScriptGenerationInput input = new ScriptGenerationInput(
                    session.getLanguageCode(),
                    session.getDifficulty(),
                    questionProperties.countFor(session.getDifficulty()),
                    UUID.fromString(session.getGenerationSeed()),
                    snapshot.getProfileJson(),
                    snapshot.getJobDescriptionText(),
                    excludedSignatures);
            return new GenerationPreparation(
                    profileId,
                    snapshot.getJobDescriptionHash(),
                    input,
                    Set.copyOf(snapshotProjectIds),
                    Set.copyOf(snapshotSkillIds),
                    history.sessionIds(),
                    history.questions());
        }));
    }

    private ScriptGenerationResult persist(
            Long sessionId,
            UUID processingToken,
            Long expectedProfileId,
            String jobDescriptionHash,
            ValidatedScript script,
            boolean diversityRetried) {
        return Objects.requireNonNull(writeTransaction.execute(status -> {
            profileRepository.findByIdForUpdate(expectedProfileId)
                    .orElseThrow(SessionNotFoundException::new);
            InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                    .orElseThrow(SessionNotFoundException::new);
            verifyGenerationClaim(session, processingToken);
            if (!expectedProfileId.equals(session.getProfile().getId())
                    || questionRepository.countBySessionId(sessionId) != 0) {
                throw new SessionInvalidStateException();
            }

            History currentHistory = loadHistory(
                    expectedProfileId,
                    jobDescriptionHash,
                    sessionId);
            validator.validateDiversity(
                    script.questions(),
                    currentHistory.sessionIds(),
                    currentHistory.questions());

            Map<Long, ProfileProject> projects = projectRepository
                    .findByProfileIdOrderByDisplayOrderAsc(expectedProfileId)
                    .stream()
                    .collect(Collectors.toMap(ProfileProject::getId, Function.identity()));
            Map<Long, ProfileSkill> skills = skillRepository
                    .findByProfileIdOrderByDisplayOrderAsc(expectedProfileId)
                    .stream()
                    .collect(Collectors.toMap(ProfileSkill::getId, Function.identity()));
            Instant now = clock.instant();
            List<SessionQuestion> questions = script.questions().stream()
                    .map(question -> toEntity(
                            session,
                            question,
                            projects,
                            skills,
                            script.promptVersion(),
                            script.modelName(),
                            now))
                    .toList();
            questionRepository.saveAll(questions);
            session.recordGeneratedQuestionCount(questions.size());

            InterviewSession readySession = stateMachine.completeScriptGeneration(
                    session,
                    processingToken,
                    TRANSITION_REASON);
            return new ScriptGenerationResult(
                    sessionId,
                    questions.size(),
                    readySession.getVersion(),
                    script.modelName(),
                    script.promptVersion(),
                    script.tokenCost(),
                    script.durationMs(),
                    diversityRetried);
        }));
    }

    private SessionQuestion toEntity(
            InterviewSession session,
            ValidatedQuestion question,
            Map<Long, ProfileProject> projects,
            Map<Long, ProfileSkill> skills,
            String promptVersion,
            String modelName,
            Instant now) {
        ProfileProject project = resolveSource(
                question.sourceProjectId(), projects, "source project");
        ProfileSkill skill = resolveSource(question.sourceSkillId(), skills, "source skill");
        return SessionQuestion.create(
                session,
                new SessionQuestion.CreationData(
                        question.ordinal(),
                        question.questionText(),
                        question.topic(),
                        question.competency(),
                        question.difficulty(),
                        question.sourceType(),
                        project,
                        skill,
                        question.sourceJdExcerpt(),
                        question.questionSignature(),
                        UUID.fromString(session.getGenerationSeed()),
                        promptVersion,
                        modelName),
                now);
    }

    private <T> T resolveSource(Long sourceId, Map<Long, T> available, String sourceName) {
        if (sourceId == null) {
            return null;
        }
        T source = available.get(sourceId);
        if (source == null) {
            throw ScriptGenerationException.invalidOutput(
                    sourceName + " no longer belongs to the selected profile");
        }
        return source;
    }

    private History loadHistory(
            Long profileId,
            String jobDescriptionHash,
            Long excludedSessionId) {
        List<Long> sessionIds = questionRepository.findRecentComparableSessionIds(
                profileId,
                jobDescriptionHash,
                excludedSessionId,
                PageRequest.of(0, DIVERSITY_HISTORY_SESSION_LIMIT));
        List<QuestionHistoryProjection> questions = sessionIds.isEmpty()
                ? List.of()
                : questionRepository.findHistoryBySessionIds(sessionIds);
        return new History(List.copyOf(sessionIds), List.copyOf(questions));
    }

    private void validateProviderContract(ScriptGenerationOutcome outcome) {
        if (outcome == null
                || !aiProperties.scriptPromptVersion().equals(outcome.promptVersion())) {
            throw ScriptGenerationException.invalidOutput(
                    "provider returned an unexpected prompt version");
        }
    }

    private void verifyGenerationClaim(InterviewSession session, UUID processingToken) {
        if (session.getStatus() != SessionStatus.SCRIPT_GENERATING
                || session.getProcessingStage() != SessionProcessingStage.SCRIPT_GENERATION) {
            throw new SessionInvalidStateException();
        }
        if (!processingToken.toString().equals(session.getProcessingToken())) {
            throw new IllegalStateException(
                    "Script generation claim is no longer owned, sessionId=" + session.getId());
        }
    }

    private static Set<Long> extractIds(JsonNode profile, String arrayField) {
        Set<Long> ids = new HashSet<>();
        JsonNode items = profile.path(arrayField);
        if (!items.isArray()) {
            return ids;
        }
        for (JsonNode item : items) {
            JsonNode id = item.path("id");
            if (id.isIntegralNumber() && id.canConvertToLong() && id.longValue() > 0) {
                ids.add(id.longValue());
            }
        }
        return ids;
    }

    private record GenerationPreparation(
            Long profileId,
            String jobDescriptionHash,
            ScriptGenerationInput input,
            Set<Long> allowedProjectIds,
            Set<Long> allowedSkillIds,
            List<Long> recentSessionIds,
            List<QuestionHistoryProjection> history) {
    }

    private record History(
            List<Long> sessionIds,
            List<QuestionHistoryProjection> questions) {
    }
}
