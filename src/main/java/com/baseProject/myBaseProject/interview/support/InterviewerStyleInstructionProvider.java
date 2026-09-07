package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.ai.PromptResourceLoader;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
public class InterviewerStyleInstructionProvider {
    public static final String STYLE_POLICY_VERSION = "v1";

    private final Map<InterviewerStyle, String> instructions;

    public InterviewerStyleInstructionProvider() throws IOException {
        // Nạp toàn bộ policy khi khởi động để fail-fast nếu thiếu bất kỳ style resource nào.
        EnumMap<InterviewerStyle, String> loaded = new EnumMap<>(InterviewerStyle.class);
        for (InterviewerStyle style : InterviewerStyle.values()) {
            loaded.put(style, PromptResourceLoader.loadRequired(pathFor(style)));
        }
        instructions = Map.copyOf(loaded);
    }

    public String instructionFor(InterviewerStyle style) {
        return instructions.get(Objects.requireNonNull(style, "Interviewer style is required"));
    }

    private String pathFor(InterviewerStyle style) {
        return switch (style) {
            case FRIENDLY -> PromptConstant.INTERVIEW_STYLE_FRIENDLY;
            case PROFESSIONAL -> PromptConstant.INTERVIEW_STYLE_PROFESSIONAL;
            case CHALLENGING -> PromptConstant.INTERVIEW_STYLE_CHALLENGING;
        };
    }
}
