package com.baseProject.myBaseProject.interview.scoring;

import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringOutcome;
import com.baseProject.myBaseProject.interview.ai.port.InterviewScorer;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ScoringPreparation;
import com.baseProject.myBaseProject.interview.scoring.model.ScoringData.ValidatedScoringResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewScoringService {

    private final InterviewScorer scorer;
    private final InterviewScoringValidator validator;
    private final InterviewAiProperties aiProperties;
    private final InterviewScoringStore store;

    public boolean scoreAndPersist(Long sessionId, UUID processingToken) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(processingToken);

        ScoringPreparation preparation = store.prepare(sessionId, processingToken);
        if (preparation == null) {
            return false;
        }
        ScoringOutcome outcome = scorer.score(preparation.input());
        ValidatedScoringResult result = validator.validate(
                outcome,
                preparation,
                aiProperties.scoringPromptVersion());
        boolean committed = store.commit(sessionId, processingToken, result);
        if (committed) {
            log.info(
                    "Interview report persisted: sessionId={}, overallScore={}, partial={}, "
                            + "assessedWeight={}, model={}, promptVersion={}, durationMs={}",
                    sessionId,
                    result.overallScore(),
                    result.partial(),
                    result.assessedWeight(),
                    result.modelName(),
                    result.promptVersion(),
                    result.durationMs());
        }
        return committed;
    }
}
