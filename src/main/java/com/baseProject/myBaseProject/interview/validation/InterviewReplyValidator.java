package com.baseProject.myBaseProject.interview.validation;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class InterviewReplyValidator {
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final int MAX_CONVERSATION_SUMMARY_LENGTH = 12000;
    private static final int MAX_EVIDENCE_SUMMARY_LENGTH = 2000;
    private static final Set<CandidateIntent> INTENTS_REQUIRING_HANDLING = EnumSet.of(
            CandidateIntent.REQUEST_REPEAT,
            CandidateIntent.REQUEST_CLARIFICATION,
            CandidateIntent.REQUEST_TIME,
            CandidateIntent.ASK_INTERVIEWER,
            CandidateIntent.SOCIAL_OR_META,
            CandidateIntent.OFF_TOPIC);

    public InterviewReplyResult validate(
            InterviewReplyResult result,
            InterviewContext context,
            boolean mustClose) {
        // Output AI không đáng tin cậy nên phải được chuẩn hóa trước khi cập nhật session.
        if (result == null || result.candidateIntent() == null || result.action() == null) {
            throw invalid("Missing candidate intent or interviewer action");
        }
        if (result.action() == InterviewTurnAction.OPENING) {
            throw invalid("Opening is not a valid action after a candidate answer");
        }
        if (mustClose && result.action() != InterviewTurnAction.CLOSE) {
            throw invalid("Interviewer must close because the session is out of time");
        }
        validateIntentAction(result.candidateIntent(), result.action(), mustClose);

        String message = required(
                result.interviewerMessage(), MAX_MESSAGE_LENGTH, "interviewer message");
        String summary = required(
                result.conversationSummary(), MAX_CONVERSATION_SUMMARY_LENGTH,
                "conversation summary");

        Map<String, InterviewContext.FocusArea> areas = new HashMap<>();
        for (InterviewContext.FocusArea area : context.focusAreas()) {
            areas.put(area.code(), area);
        }

        String focusCode = normalizeCode(result.focusAreaCode());
        if (result.action() == InterviewTurnAction.CLOSE) {
            focusCode = null;
        } else if (result.action() == InterviewTurnAction.HANDLE_REQUEST) {
            if (focusCode != null && !areas.containsKey(focusCode)) {
                throw invalid("Interviewer selected an unknown focus area");
            }
        } else if (focusCode == null || !areas.containsKey(focusCode)) {
            throw invalid("Interviewer selected an unknown focus area");
        }

        List<InterviewReplyResult.EvidenceUpdate> updates = new ArrayList<>();
        Set<String> updatedCodes = new HashSet<>();
        for (InterviewReplyResult.EvidenceUpdate update : safe(result.evidenceUpdates())) {
            if (update == null || update.status() == null) {
                throw invalid("Evidence update is incomplete");
            }
            String code = normalizeCode(update.focusAreaCode());
            InterviewContext.FocusArea area = areas.get(code);
            if (area == null || !updatedCodes.add(code)) {
                throw invalid("Evidence updates must reference unique known focus areas");
            }
            // Evidence chỉ được tiến lên để một lượt sau không xóa kết quả đã thu thập.
            if (!update.status().isAtLeast(area.evidenceStatus())) {
                throw invalid("Evidence status cannot move backwards");
            }
            updates.add(new InterviewReplyResult.EvidenceUpdate(
                    code,
                    update.status(),
                    required(update.evidenceSummary(), MAX_EVIDENCE_SUMMARY_LENGTH,
                            "evidence summary")));
        }

        return new InterviewReplyResult(
                result.candidateIntent(),
                result.action(),
                message,
                focusCode,
                summary,
                List.copyOf(updates));
    }

    private void validateIntentAction(
            CandidateIntent intent,
            InterviewTurnAction action,
            boolean mustClose) {
        if (mustClose) {
            return;
        }
        if (intent == CandidateIntent.REQUEST_END
                && action != InterviewTurnAction.CLOSE) {
            throw invalid("End request must produce a closing action");
        }
        if (action == InterviewTurnAction.CLOSE) {
            return;
        }
        if (INTENTS_REQUIRING_HANDLING.contains(intent)
                && action != InterviewTurnAction.HANDLE_REQUEST) {
            throw invalid("Candidate request must be handled before continuing");
        }
        if (intent == CandidateIntent.INAPPROPRIATE
                && action != InterviewTurnAction.HANDLE_REQUEST) {
            throw invalid("Inappropriate content must be handled or closed");
        }
    }

    private List<InterviewReplyResult.EvidenceUpdate> safe(
            List<InterviewReplyResult.EvidenceUpdate> updates) {
        return updates == null ? List.of() : updates;
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String required(String value, int maxLength, String field) {
        if (value == null) {
            throw invalid("Missing " + field);
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalid("Missing or oversized " + field);
        }
        return normalized;
    }

    private DomainException invalid(String detail) {
        return new DomainException(ErrorCode.INTERVIEW_REPLY_INVALID, detail);
    }
}
