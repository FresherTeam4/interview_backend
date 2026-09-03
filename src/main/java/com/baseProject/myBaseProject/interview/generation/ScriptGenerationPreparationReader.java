package com.baseProject.myBaseProject.interview.generation;

import com.baseProject.myBaseProject.config.properites.InterviewQuestionProperties;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.entity.SessionContextSnapshot;
import com.baseProject.myBaseProject.enums.SessionProcessingStage;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.exception.SessionInvalidStateException;
import com.baseProject.myBaseProject.exception.SessionNotFoundException;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.generation.model.GenerationPreparation;
import com.baseProject.myBaseProject.repository.ProfileProjectRepository;
import com.baseProject.myBaseProject.repository.ProfileSkillRepository;
import com.baseProject.myBaseProject.repository.SessionContextSnapshotRepository;
import com.baseProject.myBaseProject.repository.SessionQuestionRepository;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScriptGenerationPreparationReader {

    private static final int DIVERSITY_HISTORY_SESSION_LIMIT = 3;

    private final InterviewQuestionProperties questionProperties;
    private final SessionContextSnapshotRepository snapshotRepository;
    private final SessionQuestionRepository questionRepository;
    private final ProfileProjectRepository projectRepository;
    private final ProfileSkillRepository skillRepository;

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

        List<Long> recentSessionIds = questionRepository.findRecentComparableSessionIds(
                profileId,
                snapshot.getJobDescriptionHash(),
                sessionId,
                PageRequest.of(0, DIVERSITY_HISTORY_SESSION_LIMIT));
        List<QuestionHistoryProjection> history = recentSessionIds.isEmpty()
                ? List.of()
                : questionRepository.findHistoryBySessionIds(recentSessionIds);

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
                Set.copyOf(snapshotProjectIds),
                Set.copyOf(snapshotSkillIds),
                List.copyOf(recentSessionIds),
                List.copyOf(history));
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
