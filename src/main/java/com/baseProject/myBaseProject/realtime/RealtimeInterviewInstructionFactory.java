package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.ai.PromptResourceLoader;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RealtimeInterviewInstructionFactory {
    private final InterviewerStyleInstructionProvider styleInstructions;
    private final String prompt;

    public RealtimeInterviewInstructionFactory(
            InterviewerStyleInstructionProvider styleInstructions) throws IOException {
        this.styleInstructions = styleInstructions;
        prompt = PromptResourceLoader.loadRequired(
                PromptConstant.INTERVIEW_REALTIME_SYSTEM_PROMPT);
    }

    public String create(
            InterviewSession session,
            List<InterviewFocusArea> focusAreas) {
        Map<String, Object> parameters = Map.of(
                "languageName", languageName(session.getLanguageCode()),
                "languageCode", session.getLanguageCode(),
                "durationMinutes", session.getDurationMinutes(),
                "interviewerStyle", session.getInterviewerStyle().name(),
                "styleInstruction", styleInstructions.instructionFor(
                        session.getInterviewerStyle()),
                "jobContext", safe(session.getJobContextSummary()),
                "candidateContext", safe(session.getCandidateContextSummary()),
                "focusAreas", formatFocusAreas(focusAreas),
                "openingMessage", safe(session.getOpeningMessage()));
        return new PromptTemplate(prompt).render(parameters);
    }

    private String formatFocusAreas(List<InterviewFocusArea> focusAreas) {
        StringBuilder result = new StringBuilder();
        for (InterviewFocusArea area : focusAreas) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append("- [")
                    .append(area.getPriority())
                    .append("] ")
                    .append(area.getName())
                    .append(": ")
                    .append(safe(area.getDescription()))
                    .append(" Evidence goal: ")
                    .append(safe(area.getReason()))
                    .append(" Planned time: ")
                    .append(area.getPlannedSeconds())
                    .append(" seconds.");
        }
        return result.toString();
    }

    private String languageName(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "vi" -> "Vietnamese";
            case "ja" -> "Japanese";
            case "en" -> "English";
            default -> code;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
