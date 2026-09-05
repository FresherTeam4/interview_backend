package com.baseProject.myBaseProject.interview.validation;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class InterviewPlanValidator {
    private static final int MIN_FOCUS_AREAS = 2;
    private static final int MAX_FOCUS_AREAS = 8;
    private static final int MIN_PLANNED_SECONDS = 60;
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    public InterviewPlanResult validate(InterviewPlanResult plan, String expectedLanguageCode, int durationMinutes) {

        // Output AI không đáng tin cậy nên phải kiểm tra quy tắc nghiệp vụ và giới hạn cột.
        if (plan == null || !expectedLanguageCode.equalsIgnoreCase(text(plan.languageCode(), 10))) {
            throw invalid("Plan language does not match the session language");
        }

        String jobSummary = required(plan.jobContextSummary(), 4000, "job context summary");
        String candidateSummary = required(
                plan.candidateContextSummary(), 4000, "candidate context summary");
        String opening = required(plan.openingMessage(), 2000, "opening message");
        List<InterviewPlanResult.FocusArea> requested = plan.focusAreas();
        if (requested == null || requested.size() < MIN_FOCUS_AREAS
                || requested.size() > MAX_FOCUS_AREAS) {
            throw invalid("Plan must contain between 2 and 8 focus areas");
        }

        Set<String> seenCodes = new HashSet<>();
        List<InterviewPlanResult.FocusArea> focusAreas = new ArrayList<>();
        long totalPlannedSeconds = 0;
        for (InterviewPlanResult.FocusArea area : requested) {
            if (area == null || area.priority() == null) {
                throw invalid("Every focus area must have a priority");
            }
            String code = required(area.code(), 50, "focus area code")
                    .toUpperCase(Locale.ROOT);
            if (!CODE.matcher(code).matches() || !seenCodes.add(code)) {
                throw invalid("Focus area codes must be unique uppercase identifiers");
            }
            if (area.plannedSeconds() == null || area.plannedSeconds() < MIN_PLANNED_SECONDS) {
                throw invalid("Every focus area must reserve at least 60 seconds");
            }
            totalPlannedSeconds += area.plannedSeconds();
            focusAreas.add(new InterviewPlanResult.FocusArea(
                    code,
                    required(area.name(), 150, "focus area name"),
                    required(area.description(), 2000, "focus area description"),
                    area.priority(),
                    required(area.reason(), 2000, "focus area reason"),
                    area.plannedSeconds()));
        }
        if (totalPlannedSeconds > durationMinutes * 60) {
            throw invalid("Focus area time exceeds the session duration");
        }

        // Chỉ chuyển tiếp bản đã chuẩn hóa để tầng persistence không lưu trực tiếp output thô.
        return new InterviewPlanResult(
                expectedLanguageCode, jobSummary, candidateSummary, List.copyOf(focusAreas), opening);
    }

    private String required(String value, int maxLength, String field) {
        String normalized = text(value, maxLength);
        if (normalized == null) {
            throw invalid("Missing or invalid " + field);
        }
        return normalized;
    }

    private String text(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() || normalized.length() > maxLength ? null : normalized;
    }

    private DomainException invalid(String detail) {
        return new DomainException(ErrorCode.INTERVIEW_PLAN_INVALID, detail);
    }
}
