package com.baseProject.myBaseProject.interview.generation;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationResult;
import com.baseProject.myBaseProject.interview.generation.model.ValidatedQuestion;
import com.baseProject.myBaseProject.interview.generation.model.ValidatedScript;
import com.baseProject.myBaseProject.interview.lifecycle.SessionStateMachine;
import com.baseProject.myBaseProject.repository.CandidateProfileRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScriptGenerationCommitter {

    private static final int DIVERSITY_HISTORY_SESSION_LIMIT = 3;
    private static final String TRANSITION_REASON = "Generated interview script persisted";

    private final InterviewSessionRepository sessionRepository;
    private final CandidateProfileRepository profileRepository;
    private final ProfileProjectRepository projectRepository;
    private final ProfileSkillRepository skillRepository;
    private final SessionQuestionRepository questionRepository;
    private final SessionStateMachine stateMachine;
    private final QuestionDiversityPolicy diversityPolicy;
    private final Clock clock;

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

        List<Long> recentSessionIds = questionRepository.findRecentComparableSessionIds(
                expectedProfileId,
                jobDescriptionHash,
                sessionId,
                PageRequest.of(0, DIVERSITY_HISTORY_SESSION_LIMIT));
        List<QuestionHistoryProjection> history = recentSessionIds.isEmpty()
                ? List.of()
                : questionRepository.findHistoryBySessionIds(recentSessionIds);
        diversityPolicy.validateDiversity(
                script.questions(),
                recentSessionIds,
                history);

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
}
