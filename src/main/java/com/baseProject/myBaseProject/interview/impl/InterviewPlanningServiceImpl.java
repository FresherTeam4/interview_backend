package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.ai.PromptResourceLoader;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.InterviewPlanningService;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import com.baseProject.myBaseProject.interview.validation.InterviewPlanValidator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class InterviewPlanningServiceImpl implements InterviewPlanningService {
    private final AiService aiService;
    private final InterviewPlanValidator validator;
    private final InterviewerStyleInstructionProvider styleInstructions;
    private final String systemPrompt;
    private final String userPrompt;

    public InterviewPlanningServiceImpl(
            AiService aiService,
            InterviewPlanValidator validator,
            InterviewerStyleInstructionProvider styleInstructions) throws IOException {
        this.aiService = aiService;
        this.validator = validator;
        this.styleInstructions = styleInstructions;
        // Nạp cả hai prompt một lần để ứng dụng fail-fast khi thiếu resource.
        this.systemPrompt = PromptResourceLoader.loadRequired(
                PromptConstant.INTERVIEW_PLAN_SYSTEM_PROMPT);
        this.userPrompt = PromptResourceLoader.loadRequired(
                PromptConstant.INTERVIEW_PLAN_USER_PROMPT);
    }

    @Override
    public InterviewPlanResult generate(
            String templateSnapshotJson,
            String profileSnapshotJson,
            String languageCode,
            int durationMinutes,
            InterviewerStyle interviewerStyle) {
        InterviewPlanResult plan = aiService.generateStructured(
                systemPrompt,
                userPrompt,
                Map.of(
                        "templateSnapshot", templateSnapshotJson,
                        "profileSnapshot", profileSnapshotJson,
                        "languageCode", languageCode,
                        "durationMinutes", durationMinutes,
                        "durationSeconds", durationMinutes * 60,
                        "interviewerStyle", interviewerStyle.name(),
                        "styleInstruction", styleInstructions.instructionFor(interviewerStyle)),
                InterviewPlanResult.class);

        // Luôn kiểm tra output AI trước khi kế hoạch được lưu vào session.
        return validator.validate(plan, languageCode, durationMinutes);
    }
}
