package com.baseProject.myBaseProject.interview.validation;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.exception.DomainException;
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
    private static final int MAX_SUMMARY_LENGTH = 8000;
    private static final int MAX_FEEDBACK_LENGTH = 4000;
    private static final int MAX_ITEM_TEXT_LENGTH = 1000;
    private static final int MAX_LIST_ITEMS = 5;
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
                List.copyOf(assessments),
                communicationScore,
                OUTPUT.required(result.communicationFeedback(), MAX_FEEDBACK_LENGTH,
                        "communication feedback"),
                validateReportItems(
                        result.strengths(), candidateTurnIds, true, "strength"),
                validateReportItems(
                        result.improvements(), candidateTurnIds, false, "improvement"),
                validateActionPlan(result.actionPlan()));
    }

    private InterviewAssessmentResult.FocusAreaAssessment validateFocusArea(
            InterviewAssessmentResult.FocusAreaAssessment assessment,
            Map<String, InterviewScoringContext.FocusArea> knownAreas,
            Set<String> assessedCodes,
            Set<Long> candidateTurnIds) {
        if (assessment == null || assessment.confidence() == null
                || assessment.evidenceStatus() == null) {
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
            if (score != null || !evidenceIds.isEmpty()
                    || assessment.confidence() != InterviewAssessmentConfidence.LOW) {
                throw OUTPUT.invalid(
                        "An unexplored focus area must be unscored with low confidence");
            }
        } else if (assessment.evidenceStatus() == InterviewEvidenceStatus.PARTIAL
                && assessment.confidence() == InterviewAssessmentConfidence.HIGH) {
            throw OUTPUT.invalid("Partial evidence cannot produce high confidence");
        } else if (score == null || evidenceIds.isEmpty()) {
            throw OUTPUT.invalid("A scored focus area must cite candidate evidence");
        }

        return new InterviewAssessmentResult.FocusAreaAssessment(
                code,
                score,
                assessment.confidence(),
                assessment.evidenceStatus(),
                OUTPUT.required(
                        assessment.rationale(), MAX_FEEDBACK_LENGTH, "focus rationale"),
                validateTextList(assessment.strengths(), "focus strength"),
                validateTextList(assessment.gaps(), "focus gap"),
                validateFocusFeedback(assessment.feedback(), code),
                evidenceIds);
    }

    private String validateFocusFeedback(String value, String focusAreaCode) {
        if (value == null) {
            throw invalidFocusFeedback(focusAreaCode, "MISSING", null);
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw invalidFocusFeedback(focusAreaCode, "BLANK", 0);
        }
        if (normalized.length() > MAX_FEEDBACK_LENGTH) {
            throw invalidFocusFeedback(
                    focusAreaCode, "OVERSIZED", normalized.length());
        }
        return normalized;
    }

    private DomainException invalidFocusFeedback(
            String focusAreaCode, String reason, Integer normalizedLength) {
        String detail = ("Invalid focus feedback: focusAreaCode=%s, reason=%s, "
                + "normalizedLength=%s, maxLength=%d").formatted(
                        focusAreaCode,
                        reason,
                        normalizedLength == null ? "null" : normalizedLength,
                        MAX_FEEDBACK_LENGTH);
        return OUTPUT.invalid(detail);
    }

    private List<InterviewAssessmentResult.ReportItem> validateReportItems(
            List<InterviewAssessmentResult.ReportItem> items,
            Set<Long> candidateTurnIds,
            boolean evidenceRequired,
            String field) {
        List<InterviewAssessmentResult.ReportItem> safeItems = OUTPUT.safe(items);
        if (safeItems.size() > MAX_LIST_ITEMS) {
            throw OUTPUT.invalid("Too many " + field + " items");
        }
        List<InterviewAssessmentResult.ReportItem> normalized = new ArrayList<>();
        for (InterviewAssessmentResult.ReportItem item : safeItems) {
            if (item == null) {
                throw OUTPUT.invalid("Incomplete " + field + " item");
            }
            List<Long> evidenceIds = validateEvidenceIds(
                    item.evidenceTurnIds(), candidateTurnIds, field + " evidence");
            if (evidenceRequired && evidenceIds.isEmpty()) {
                throw OUTPUT.invalid("A strength must cite candidate evidence");
            }
            normalized.add(new InterviewAssessmentResult.ReportItem(
                    OUTPUT.required(item.title(), MAX_ITEM_TEXT_LENGTH, field + " title"),
                    OUTPUT.required(item.description(), MAX_ITEM_TEXT_LENGTH,
                            field + " description"),
                    evidenceIds));
        }
        return List.copyOf(normalized);
    }

    private List<InterviewAssessmentResult.ActionPlanItem> validateActionPlan(
            List<InterviewAssessmentResult.ActionPlanItem> items) {
        List<InterviewAssessmentResult.ActionPlanItem> safeItems = OUTPUT.safe(items);
        if (safeItems.size() > MAX_LIST_ITEMS) {
            throw OUTPUT.invalid("Action plan contains too many items");
        }
        Set<Integer> priorities = new HashSet<>();
        List<InterviewAssessmentResult.ActionPlanItem> normalized = new ArrayList<>();
        for (InterviewAssessmentResult.ActionPlanItem item : safeItems) {
            if (item == null || item.priority() == null
                    || item.priority() < 1 || item.priority() > MAX_LIST_ITEMS
                    || !priorities.add(item.priority())) {
                throw OUTPUT.invalid(
                        "Action plan priorities must be unique values from 1 to 5");
            }
            normalized.add(new InterviewAssessmentResult.ActionPlanItem(
                    item.priority(),
                    OUTPUT.required(item.action(), MAX_ITEM_TEXT_LENGTH, "action"),
                    OUTPUT.required(item.reason(), MAX_ITEM_TEXT_LENGTH, "action reason"),
                    OUTPUT.required(item.suggestion(), MAX_ITEM_TEXT_LENGTH,
                            "action suggestion")));
        }
        return List.copyOf(normalized);
    }

    private List<String> validateTextList(List<String> values, String field) {
        List<String> safeValues = OUTPUT.safe(values);
        if (safeValues.size() > MAX_LIST_ITEMS) {
            throw OUTPUT.invalid("Too many " + field + " items");
        }
        return safeValues.stream()
                .map(value -> OUTPUT.required(value, MAX_ITEM_TEXT_LENGTH, field))
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
