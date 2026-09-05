package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.InterviewPlanningService;
import com.baseProject.myBaseProject.interview.validation.InterviewPlanValidator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class InterviewPlanningServiceImpl implements InterviewPlanningService {
    private final AiService aiService;
    private final InterviewPlanValidator validator;
    private final String prompt;

    public InterviewPlanningServiceImpl(
            AiService aiService, InterviewPlanValidator validator) throws IOException {
        this.aiService = aiService;
        this.validator = validator;
        this.prompt = new ClassPathResource(PromptConstant.INTERVIEW_PLAN_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public InterviewPlanResult generate(
            String templateSnapshotJson,
            String profileSnapshotJson,
            String languageCode,
            int durationMinutes,
            InterviewerStyle interviewerStyle) {
        InterviewPlanResult plan = aiService.generateStructured(
                prompt,
                Map.of(
                        "templateSnapshot", templateSnapshotJson,
                        "profileSnapshot", profileSnapshotJson,
                        "languageCode", languageCode,
                        "durationMinutes", durationMinutes,
                        "durationSeconds", durationMinutes * 60,
                        "interviewerStyle", interviewerStyle.name()),
                InterviewPlanResult.class);

        // Luôn kiểm tra output AI trước khi kế hoạch được lưu vào session.
        return validator.validate(plan, languageCode, durationMinutes);
    }
}
