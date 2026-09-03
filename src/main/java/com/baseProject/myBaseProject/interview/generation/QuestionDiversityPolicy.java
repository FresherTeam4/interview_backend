package com.baseProject.myBaseProject.interview.generation;

import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.generation.model.ValidatedQuestion;
import com.baseProject.myBaseProject.repository.projection.QuestionHistoryProjection;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class QuestionDiversityPolicy {

    private final QuestionSignatureFactory signatureFactory;

    public void validateDiversity(
            List<ValidatedQuestion> questions,
            List<Long> recentSessionIds,
            List<QuestionHistoryProjection> history) {
        if (history.isEmpty()) {
            return;
        }

        Set<String> historicalTexts = new HashSet<>();
        for (QuestionHistoryProjection previous : history) {
            historicalTexts.add(signatureFactory.normalizeText(previous.questionText()));
        }
        boolean repeatedText = questions.stream()
                .map(ValidatedQuestion::questionText)
                .map(signatureFactory::normalizeText)
                .anyMatch(historicalTexts::contains);
        if (repeatedText) {
            throw ScriptGenerationException.diversityRejected(
                    "a normalized question repeats one of the three recent sessions");
        }

        if (recentSessionIds.isEmpty()) {
            return;
        }
        Long nearestSessionId = recentSessionIds.get(0);
        Set<String> nearestSignatures = new HashSet<>();
        for (QuestionHistoryProjection previous : history) {
            if (nearestSessionId.equals(previous.sessionId())) {
                nearestSignatures.add(previous.questionSignature());
            }
        }
        long differentCount = questions.stream()
                .map(ValidatedQuestion::questionSignature)
                .filter(signature -> !nearestSignatures.contains(signature))
                .count();
        if (differentCount * 10 < questions.size() * 7L) {
            throw ScriptGenerationException.diversityRejected(
                    "less than 70 percent of signatures differ from the nearest session");
        }
    }
}
