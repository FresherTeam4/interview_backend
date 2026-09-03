package com.baseProject.myBaseProject.interview.generation;

import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationOutcome;
import com.baseProject.myBaseProject.interview.ai.port.InterviewQuestionGenerator;
import com.baseProject.myBaseProject.interview.generation.model.GenerationPreparation;
import com.baseProject.myBaseProject.interview.generation.model.ScriptGenerationResult;
import com.baseProject.myBaseProject.interview.generation.model.ValidatedScript;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewScriptGenerationService {

    private final InterviewQuestionGenerator questionGenerator;
    private final QuestionScriptValidator validator;
    private final InterviewAiProperties aiProperties;
    private final ScriptGenerationPreparationReader preparationReader;
    private final ScriptGenerationCommitter committer;

    /**
     * Calls the provider without a database transaction, then commits the complete script and READY
     * transition together. Only a diversity rejection receives one immediate regeneration attempt.
     */
    public ScriptGenerationResult generateAndPersist(Long sessionId, UUID processingToken) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(processingToken);

        for (int attempt = 0; attempt < 2; attempt++) {
            GenerationPreparation preparation = preparationReader.prepare(sessionId, processingToken);
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
                ScriptGenerationResult result = committer.commit(
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

    private void validateProviderContract(ScriptGenerationOutcome outcome) {
        if (outcome == null
                || !aiProperties.scriptPromptVersion().equals(outcome.promptVersion())) {
            throw ScriptGenerationException.invalidOutput(
                    "provider returned an unexpected prompt version");
        }
    }
}
