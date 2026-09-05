package com.baseProject.myBaseProject.jobdescription.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.jobdescription.JobDescriptionAnalysisService;
import com.baseProject.myBaseProject.jobdescription.validation.JobAnalysisValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class JobDescriptionAnalysisServiceImpl implements JobDescriptionAnalysisService {
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final JobAnalysisValidator validator;
    private final String prompt;

    public JobDescriptionAnalysisServiceImpl(AiService aiService, ObjectMapper objectMapper,
                                             JobAnalysisValidator validator) throws IOException {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.prompt = new ClassPathResource(PromptConstant.JOB_DESCRIPTION_ANALYSIS_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public JobAnalysis analyze(String title, String jobDescriptionText) {
        String context = objectMapper.writeValueAsString(
                Map.of("title", title, "jobDescriptionText", jobDescriptionText));
        JobAnalysis analysis = aiService.generateStructured(
                prompt, Map.of("context", context), JobAnalysis.class);

        return validator.validate(analysis, jobDescriptionText);
    }
}
