package com.baseProject.myBaseProject.interview.validation;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class InterviewAssessmentValidator {
    private static final AiOutputValidationSupport OUTPUT =
            new AiOutputValidationSupport(
                    ErrorCode.INTERVIEW_ASSESSMENT_INVALID, "Missing or oversized %s");
    private static final int MAX_SUMMARY_LENGTH = 400;
    private static final int MAX_FEEDBACK_LENGTH = 300;
    private static final int MAX_RECOMMENDATION_LENGTH = 200;
    private static final int MAX_RECOMMENDATIONS = 3;
    private static final int MAX_EVIDENCE_TURNS = 20;

    public InterviewAssessmentResult validate(
            InterviewAssessmentResult result, InterviewScoringContext context) {
        // Kết quả AI chỉ được dùng sau khi mọi mã focus area và evidence turn đã được xác minh.
        if (result == null || context == null || context.focusAreas().isEmpty()) {
            throw OUTPUT.invalid("Missing assessment result or scoring focus areas");
        }

        Map<String, InterviewScoringContext.FocusArea> knownAreas = new HashMap<>();
        for (InterviewScoringContext.FocusArea area : context.focusAreas()) {
            if (area == null || area.code() == null) {
                throw OUTPUT.invalid("Scoring context contains an invalid focus area");
            }
            knownAreas.put(area.code().toUpperCase(Locale.ROOT), area);
        }
        Set<Long> candidateTurnIds = new HashSet<>();
        // Chỉ candidate turn có thật trong transcript mới được phép làm evidence.
        for (InterviewScoringContext.Turn turn : context.turns()) {
            if (turn != null && turn.id() != null
                    && turn.role() == InterviewTurnRole.CANDIDATE) {
                candidateTurnIds.add(turn.id());
            }
        }

        List<InterviewAssessmentResult.FocusAreaAssessment> requested =
                result.focusAreaAssessments();
        if (requested == null || requested.size() != knownAreas.size()) {
            throw OUTPUT.invalid("Assessment must contain every focus area exactly once");
        }

        Set<String> assessedCodes = new HashSet<>();
        List<InterviewAssessmentResult.FocusAreaAssessment> assessments = new ArrayList<>();
        // Mỗi focus area phải xuất hiện đúng một lần để calculator không bỏ sót hoặc tính trùng.
        for (InterviewAssessmentResult.FocusAreaAssessment assessment : requested) {
            assessments.add(validateFocusArea(
                    assessment, knownAreas, assessedCodes, candidateTurnIds));
        }
        if (!assessedCodes.equals(knownAreas.keySet())) {
            throw OUTPUT.invalid("Assessment must contain every focus area exactly once");
        }

        Integer communicationScore = optionalScore(
                result.communicationScore(), "communication score");
        if (candidateTurnIds.isEmpty() && communicationScore != null) {
            throw OUTPUT.invalid("Communication cannot be scored without a candidate turn");
        }

        return new InterviewAssessmentResult(
                OUTPUT.required(result.overallSummary(), MAX_SUMMARY_LENGTH, "overall summary"),
                OUTPUT.required(result.technicalFeedback(), MAX_FEEDBACK_LENGTH,
                        "technical feedback"),
                List.copyOf(assessments),
                communicationScore,
                OUTPUT.required(result.communicationFeedback(), MAX_FEEDBACK_LENGTH,
                        "communication feedback"),
                validateRecommendations(result.recommendations()));
    }

    private InterviewAssessmentResult.FocusAreaAssessment validateFocusArea(
            InterviewAssessmentResult.FocusAreaAssessment assessment,
            Map<String, InterviewScoringContext.FocusArea> knownAreas,
            Set<String> assessedCodes,
            Set<Long> candidateTurnIds) {
        if (assessment == null || assessment.evidenceStatus() == null) {
            throw OUTPUT.invalid("Focus area assessment is incomplete");
        }
        String code = OUTPUT.normalizeCode(assessment.focusAreaCode());
        if (!knownAreas.containsKey(code) || !assessedCodes.add(code)) {
            throw OUTPUT.invalid(
                    "Focus area assessments must reference unique known areas");
        }

        List<Long> evidenceIds = validateEvidenceIds(
                assessment.evidenceTurnIds(), candidateTurnIds,
                "focus area evidence");
        Integer score = optionalScore(assessment.score(), "focus area score");
        if (assessment.evidenceStatus() == InterviewEvidenceStatus.NOT_EXPLORED) {
            if (score != null || !evidenceIds.isEmpty()) {
                throw OUTPUT.invalid(
                        "An unexplored focus area must be unscored without evidence");
            }
        } else if (score == null || evidenceIds.isEmpty()) {
            throw OUTPUT.invalid("A scored focus area must cite candidate evidence");
        }

        return new InterviewAssessmentResult.FocusAreaAssessment(
                code,
                score,
                assessment.evidenceStatus(),
                evidenceIds);
    }

    private List<String> validateRecommendations(List<String> values) {
        List<String> safeValues = OUTPUT.safe(values);
        if (safeValues.size() > MAX_RECOMMENDATIONS) {
            throw OUTPUT.invalid("Too many recommendations");
        }
        return safeValues.stream()
                .map(value -> OUTPUT.required(
                        value, MAX_RECOMMENDATION_LENGTH, "recommendation"))
                .toList();
    }

    private List<Long> validateEvidenceIds(
            List<Long> values, Set<Long> candidateTurnIds, String field) {
        List<Long> safeValues = OUTPUT.safe(values);
        if (safeValues.size() > MAX_EVIDENCE_TURNS) {
            throw OUTPUT.invalid("Too many " + field + " references");
        }
        Set<Long> unique = new HashSet<>();
        for (Long id : safeValues) {
            if (id == null || !candidateTurnIds.contains(id) || !unique.add(id)) {
                throw OUTPUT.invalid(field + " must reference unique candidate turns");
            }
        }
        return List.copyOf(safeValues);
    }

    private Integer optionalScore(Integer value, String field) {
        if (value != null && (value < 0 || value > 100)) {
            throw OUTPUT.invalid(field + " must be between 0 and 100");
        }
        return value;
    }
}
