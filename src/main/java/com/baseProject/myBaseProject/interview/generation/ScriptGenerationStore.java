package com.baseProject.myBaseProject.interview.generation;

import com.baseProject.myBaseProject.config.properites.InterviewQuestionProperties;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationContract.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationData.GenerationPreparation;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationData.ScriptGenerationResult;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationData.ValidatedQuestion;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationData.ValidatedScript;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Transactional persistence boundary for the script-generation workflow. */
@Component
@RequiredArgsConstructor
public class ScriptGenerationStore {

    private static final int DIVERSITY_HISTORY_SESSION_LIMIT = 3;
    private static final String TRANSITION_REASON = "Generated interview script persisted";

    private final InterviewQuestionProperties questionProperties;
    private final InterviewSessionRepository sessionRepository;
    private final CandidateProfileRepository profileRepository;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionStateMachine stateMachine;
    private final QuestionDiversityPolicy diversityPolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GenerationPreparation prepare(Long sessionId, UUID processingToken) {
        SessionContextSnapshot snapshot = snapshotRepository.findBySessionId(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        InterviewSession session = snapshot.getSession();
        verifyGenerationClaim(session, processingToken);
        if (questionRepository.countBySessionId(sessionId) != 0) {
            throw new SessionInvalidStateException();
        }

        Long profileId = session.getProfile().getId();
        Set<Long> projectIds = extractIds(snapshot.getProfileJson(), "projects");
        Set<Long> skillIds = extractIds(snapshot.getProfileJson(), "skills");

        List<Long> recentSessionIds = recentSessionIds(
                profileId, snapshot.getJobDescriptionHash(), sessionId);
        List<QuestionHistoryProjection> history = history(recentSessionIds);
        Set<String> excludedSignatures = history.stream()
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
                Set.copyOf(projectIds),
                Set.copyOf(skillIds),
                List.copyOf(recentSessionIds),
                List.copyOf(history));
    }

    @Transactional
    public ScriptGenerationResult commit(
            Long sessionId,
            UUID processingToken,
            Long expectedProfileId,
            String jobDescriptionHash,
            ValidatedScript script,
            boolean diversityRetried) {
        profileRepository.findByIdForUpdate(expectedProfileId)
                .orElseThrow(SessionNotFoundException::new);
        InterviewSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(SessionNotFoundException::new);
        verifyGenerationClaim(session, processingToken);
        if (!expectedProfileId.equals(session.getProfile().getId())
                || questionRepository.countBySessionId(sessionId) != 0) {
            throw new SessionInvalidStateException();
        }

        List<Long> recentSessionIds = recentSessionIds(
                expectedProfileId, jobDescriptionHash, sessionId);
        diversityPolicy.validateDiversity(
                script.questions(), recentSessionIds, history(recentSessionIds));

        Instant now = clock.instant();
        List<SessionQuestion> questions = script.questions().stream()
                .map(question -> toEntity(
                        session,
                        question,
                        script.promptVersion(),
                        script.modelName(),
                        now))
                .toList();
        questionRepository.saveAll(questions);
        session.recordGeneratedQuestionCount(questions.size());

        InterviewSession readySession = stateMachine.completeScriptGeneration(
                session, processingToken, TRANSITION_REASON);
        return new ScriptGenerationResult(
                sessionId,
                questions.size(),
                readySession.getVersion(),
                script.modelName(),
                script.promptVersion(),
                script.tokenCost(),
                script.durationMs(),
                diversityRetried);
    }

    private List<Long> recentSessionIds(
            Long profileId,
            String jobDescriptionHash,
            Long excludedSessionId) {
        return questionRepository.findRecentComparableSessionIds(
                profileId,
                jobDescriptionHash,
                excludedSessionId,
                PageRequest.of(0, DIVERSITY_HISTORY_SESSION_LIMIT));
    }

    private List<QuestionHistoryProjection> history(List<Long> sessionIds) {
        return sessionIds.isEmpty()
                ? List.of()
                : questionRepository.findHistoryBySessionIds(sessionIds);
    }

    private SessionQuestion toEntity(
            InterviewSession session,
            ValidatedQuestion question,
            String promptVersion,
            String modelName,
            Instant now) {
        return SessionQuestion.create(
                session,
                new SessionQuestion.CreationData(
                        question.ordinal(),
                        question.questionText(),
                        question.topic(),
                        question.competency(),
                        question.difficulty(),
                        question.sourceType(),
                        question.sourceProjectId(),
                        question.sourceSkillId(),
                        question.sourceJdExcerpt(),
                        question.questionSignature(),
                        UUID.fromString(session.getGenerationSeed()),
                        promptVersion,
                        modelName),
                now);
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
}
