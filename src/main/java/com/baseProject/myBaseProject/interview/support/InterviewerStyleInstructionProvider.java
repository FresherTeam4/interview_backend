package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class InterviewerStyleInstructionProvider {
    public static final String STYLE_POLICY_VERSION = "v1";

    private final Map<InterviewerStyle, String> instructions;

    public InterviewerStyleInstructionProvider() throws IOException {
        // Nạp toàn bộ policy khi khởi động để fail-fast nếu thiếu bất kỳ style resource nào.
        instructions = Map.of(
                InterviewerStyle.FRIENDLY, load(PromptConstant.INTERVIEW_STYLE_FRIENDLY),
                InterviewerStyle.PROFESSIONAL, load(PromptConstant.INTERVIEW_STYLE_PROFESSIONAL),
                InterviewerStyle.CHALLENGING, load(PromptConstant.INTERVIEW_STYLE_CHALLENGING));
    }

    public String instructionFor(InterviewerStyle style) {
        return instructions.get(style);
    }

    private String load(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
    }
}
