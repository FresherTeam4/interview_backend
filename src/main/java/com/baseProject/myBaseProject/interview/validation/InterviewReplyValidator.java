package com.baseProject.myBaseProject.interview.validation;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InterviewReplyValidator {
    private static final AiOutputValidationSupport OUTPUT =
            new AiOutputValidationSupport(
                    ErrorCode.INTERVIEW_REPLY_INVALID, "Missing or oversized %s");
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
            throw OUTPUT.invalid("Missing candidate intent or interviewer action");
        }
        if (result.action() == InterviewTurnAction.OPENING) {
            throw OUTPUT.invalid("Opening is not a valid action after a candidate answer");
        }
        if (mustClose && result.action() != InterviewTurnAction.CLOSE) {
            throw OUTPUT.invalid("Interviewer must close because the session is out of time");
        }
        validateIntentAction(result.candidateIntent(), result.action(), mustClose);

        String message = OUTPUT.required(
                result.interviewerMessage(), MAX_MESSAGE_LENGTH, "interviewer message");
        String summary = OUTPUT.required(
                result.conversationSummary(), MAX_CONVERSATION_SUMMARY_LENGTH,
                "conversation summary");

        Map<String, InterviewContext.FocusArea> areas = new HashMap<>();
        for (InterviewContext.FocusArea area : context.focusAreas()) {
            areas.put(area.code(), area);
        }

        String focusCode = OUTPUT.normalizeCode(result.focusAreaCode());
        if (result.action() == InterviewTurnAction.CLOSE) {
            focusCode = null;
        } else if (result.action() == InterviewTurnAction.HANDLE_REQUEST) {
            if (focusCode != null && !areas.containsKey(focusCode)) {
                throw OUTPUT.invalid("Interviewer selected an unknown focus area");
            }
        } else if (focusCode == null || !areas.containsKey(focusCode)) {
            throw OUTPUT.invalid("Interviewer selected an unknown focus area");
        }

        List<InterviewReplyResult.EvidenceUpdate> updates = new ArrayList<>();
        Set<String> updatedCodes = new HashSet<>();
        for (InterviewReplyResult.EvidenceUpdate update : OUTPUT.safe(result.evidenceUpdates())) {
            if (update == null || update.status() == null) {
                throw OUTPUT.invalid("Evidence update is incomplete");
            }
            String code = OUTPUT.normalizeCode(update.focusAreaCode());
            InterviewContext.FocusArea area = areas.get(code);
            if (area == null || !updatedCodes.add(code)) {
                throw OUTPUT.invalid(
                        "Evidence updates must reference unique known focus areas");
            }
            // Evidence chỉ được tiến lên để một lượt sau không xóa kết quả đã thu thập.
            if (!update.status().isAtLeast(area.evidenceStatus())) {
                throw OUTPUT.invalid("Evidence status cannot move backwards");
            }
            updates.add(new InterviewReplyResult.EvidenceUpdate(
                    code,
                    update.status(),
                    OUTPUT.required(update.evidenceSummary(), MAX_EVIDENCE_SUMMARY_LENGTH,
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
            throw OUTPUT.invalid("End request must produce a closing action");
        }
        if (action == InterviewTurnAction.CLOSE) {
            return;
        }
        if (INTENTS_REQUIRING_HANDLING.contains(intent)
                && action != InterviewTurnAction.HANDLE_REQUEST) {
            throw OUTPUT.invalid("Candidate request must be handled before continuing");
        }
        if (intent == CandidateIntent.INAPPROPRIATE
                && action != InterviewTurnAction.HANDLE_REQUEST) {
            throw OUTPUT.invalid("Inappropriate content must be handled or closed");
        }
    }
}
