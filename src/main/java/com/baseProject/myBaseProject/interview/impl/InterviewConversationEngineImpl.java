package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.interview.InterviewConversationEngine;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.model.InterviewTurnContext;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import com.baseProject.myBaseProject.interview.validation.InterviewReplyValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class InterviewConversationEngineImpl implements InterviewConversationEngine {
    public static final String CONVERSATION_PROMPT_VERSION =
            "v2-style-" + InterviewerStyleInstructionProvider.STYLE_POLICY_VERSION;
    private static final long CLOSING_WINDOW_SECONDS = 45;

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final InterviewReplyValidator validator;
    private final InterviewerStyleInstructionProvider styleInstructions;
    private final String systemPrompt;
    private final String userPrompt;

    public InterviewConversationEngineImpl(
            AiService aiService,
            ObjectMapper objectMapper,
            InterviewReplyValidator validator,
            InterviewerStyleInstructionProvider styleInstructions) throws IOException {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.styleInstructions = styleInstructions;

        this.systemPrompt = load(PromptConstant.INTERVIEW_CONVERSATION_SYSTEM_PROMPT);
        this.userPrompt = load(PromptConstant.INTERVIEW_CONVERSATION_USER_PROMPT);
    }

    @Override
    public InterviewReplyResult reply(
            InterviewContext context,
            List<InterviewTurnContext> recentTurns,
            long remainingSeconds) {

        // check tgian kết thúc cho AI xử lý kết thúc thay vì xây thêm câu hỏi mới
        boolean mustClose = remainingSeconds <= CLOSING_WINDOW_SECONDS;
        InterviewReplyResult result = aiService.generateStructured(
                systemPrompt,
                userPrompt,
                Map.ofEntries(
                        Map.entry("languageCode", context.languageCode()),
                        Map.entry("durationMinutes", context.durationMinutes()),
                        Map.entry("remainingSeconds", Math.max(remainingSeconds, 0)),
                        Map.entry("mustClose", mustClose),
                        Map.entry("interviewerStyle", context.interviewerStyle().name()),
                        Map.entry("styleInstruction", styleInstructions.instructionFor(
                                context.interviewerStyle())),
                        Map.entry("templateSnapshot", objectMapper.writeValueAsString(
                                context.template())),
                        Map.entry("candidateSnapshot", objectMapper.writeValueAsString(
                                context.candidate())),
                        Map.entry("jobSummary", textOrEmpty(context.jobContextSummary())),
                        Map.entry("candidateSummary", textOrEmpty(
                                context.candidateContextSummary())),
                        Map.entry("conversationSummary", textOrEmpty(
                                context.conversationSummary())),
                        Map.entry("focusAreas", objectMapper.writeValueAsString(
                                context.focusAreas())),
                        Map.entry("recentTurns", objectMapper.writeValueAsString(recentTurns))),
                InterviewReplyResult.class);

        return validator.validate(result, context, mustClose);
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String load(String path) throws IOException {
        return new ClassPathResource(path)
            .getContentAsString(StandardCharsets.UTF_8)
            .strip();
    }
}
