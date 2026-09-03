package com.baseProject.myBaseProject.interview.turn;

import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionOutcome;
import com.baseProject.myBaseProject.interview.ai.port.InterviewFollowUpDecider;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnOutcome;
import com.baseProject.myBaseProject.interview.turn.model.NextTurnPreparation;
import com.baseProject.myBaseProject.interview.turn.model.ValidatedFollowUpDecision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewAdaptiveNextTurnService {

    private final InterviewFollowUpDecider followUpDecider;
    private final FollowUpDecisionValidator decisionValidator;
    private final InterviewAiProperties aiProperties;
    private final NextTurnPreparationReader preparationReader;
    private final NextTurnCommitter committer;

    /** Loads bounded context, calls AI outside a transaction, then atomically commits its decision. */
    public NextTurnOutcome decideAndPersist(Long sessionId, UUID processingToken) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(processingToken);
        NextTurnPreparation preparation = preparationReader.prepare(sessionId, processingToken);
        if (preparation == null) {
            return NextTurnOutcome.IGNORED;
        }

        ValidatedFollowUpDecision decision;
        int providerDurationMs = 0;
        FollowUpDecisionInput input = preparation.input();
        if (input.remainingQuestionFollowUps() == 0
                || input.remainingSessionFollowUps() == 0) {
            decision = ValidatedFollowUpDecision.nextQuestion(true);
        } else {
            FollowUpDecisionOutcome outcome = followUpDecider.decide(input);
            decision = decisionValidator.validate(
                    outcome,
                    input,
                    aiProperties.followUpPromptVersion());
            providerDurationMs = outcome.durationMs();
            log.info(
                    "Interview follow-up provider call completed: sessionId={}, model={}, "
                            + "promptVersion={}, durationMs={}, tokenCost={}",
                    sessionId,
                    outcome.modelName(),
                    outcome.promptVersion(),
                    outcome.durationMs(),
                    outcome.tokenCost());
        }

        NextTurnOutcome result = committer.commit(
                sessionId,
                processingToken,
                preparation.candidateTurnId(),
                preparation.questionId(),
                decision,
                providerDurationMs);
        log.info(
                "Interview next-turn decision persisted: sessionId={}, outcome={}, "
                        + "serverOverridden={}",
                sessionId,
                result,
                decision.serverOverridden());
        return result;
    }
}
