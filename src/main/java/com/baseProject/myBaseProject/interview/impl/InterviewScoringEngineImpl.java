package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.ai.PromptResourceLoader;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.interview.InterviewScoringEngine;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.validation.InterviewAssessmentValidator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@Service
public class InterviewScoringEngineImpl implements InterviewScoringEngine {
    public static final String ASSESSMENT_SCHEMA_VERSION = "v1";
    public static final String ASSESSMENT_PROMPT_VERSION = "v2";

    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final InterviewAssessmentValidator validator;
    private final String systemPrompt;
    private final String userPrompt;

    public InterviewScoringEngineImpl(
            AiService aiService,
            ObjectMapper objectMapper,
            InterviewAssessmentValidator validator) throws IOException {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        // Nạp prompt khi khởi động để thiếu resource sẽ làm ứng dụng fail-fast.
        this.systemPrompt = PromptResourceLoader.loadRequired(
                PromptConstant.INTERVIEW_SCORING_SYSTEM_PROMPT);
        this.userPrompt = PromptResourceLoader.loadRequired(
                PromptConstant.INTERVIEW_SCORING_USER_PROMPT);
    }

    @Override
    public InterviewAssessmentResult assess(InterviewScoringContext context) {
        // Transcript và snapshot được serialize thành dữ liệu, không được coi là chỉ dẫn cho AI.
        InterviewAssessmentResult result = aiService.generateStructured(
                systemPrompt,
                userPrompt,
                Map.of(
                        "languageCode", context.languageCode(),
                        "endReason", context.endReason() == null
                                ? "" : context.endReason().name(),
                        "actualDurationSeconds", context.actualDurationSeconds(),
                        "jobTemplate", objectMapper.writeValueAsString(context.jobTemplate()),
                        "jobSummary", textOrEmpty(context.jobContextSummary()),
                        "conversationSummary", textOrEmpty(context.conversationSummary()),
                        "focusAreas", objectMapper.writeValueAsString(context.focusAreas()),
                        "turns", objectMapper.writeValueAsString(context.turns())),
                InterviewAssessmentResult.class);

        return validator.validate(result, context);
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
