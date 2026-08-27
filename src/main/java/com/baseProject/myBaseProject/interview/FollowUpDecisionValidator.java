package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.enums.FollowUpDecision;
import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.interview.ai.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.FollowUpDecisionOutcome;
import com.baseProject.myBaseProject.interview.ai.GeneratedFollowUpDecision;

import org.springframework.stereotype.Component;

@Component
class FollowUpDecisionValidator {

    private static final int QUESTION_TEXT_MAX_LENGTH = 2000;
    private static final int EVIDENCE_QUOTE_MAX_LENGTH = 2000;

    ValidatedFollowUpDecision validate(
            FollowUpDecisionOutcome outcome,
            FollowUpDecisionInput input,
            String expectedPromptVersion) {
        validateMetadata(outcome, expectedPromptVersion);
        GeneratedFollowUpDecision generated = outcome.result();
        if (generated == null || generated.decision() == null) {
            throw FollowUpDecisionException.invalidOutput("decision is missing");
        }

        if (generated.decision() != FollowUpDecision.FOLLOW_UP) {
            return ValidatedFollowUpDecision.nextQuestion(
                    generated.decision() == FollowUpDecision.END_INTERVIEW);
        }
        if (input.remainingQuestionFollowUps() == 0
                || input.remainingSessionFollowUps() == 0) {
            return ValidatedFollowUpDecision.nextQuestion(true);
        }

        String questionText = safePlainText(
                generated.questionText(), QUESTION_TEXT_MAX_LENGTH);
        String evidenceQuote = safePlainText(
                generated.evidenceQuote(), EVIDENCE_QUOTE_MAX_LENGTH);
        if (questionText == null
                || evidenceQuote == null
                || !input.candidateAnswer().contains(evidenceQuote)) {
            return ValidatedFollowUpDecision.nextQuestion(true);
        }
        return ValidatedFollowUpDecision.followUp(questionText, evidenceQuote);
    }

    private void validateMetadata(
            FollowUpDecisionOutcome outcome,
            String expectedPromptVersion) {
        if (outcome == null
                || outcome.modelName() == null
                || outcome.modelName().isBlank()
                || outcome.modelName().strip().length() > 100
                || outcome.promptVersion() == null
                || !expectedPromptVersion.equals(outcome.promptVersion().strip())
                || outcome.durationMs() < 0
                || outcome.tokenCost() != null && outcome.tokenCost() < 0) {
            throw FollowUpDecisionException.invalidOutput(
                    "provider returned invalid metadata or prompt version");
        }
    }

    private String safePlainText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength
                || normalized.indexOf('\0') >= 0
                || normalized.contains("```")
                || normalized.contains("~~~")) {
            return null;
        }
        return normalized;
    }
}
