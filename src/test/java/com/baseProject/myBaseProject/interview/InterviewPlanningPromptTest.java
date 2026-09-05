package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPlanningPromptTest {
    @Test
    void promptsSeparateTrustedStylePolicyFromUntrustedSnapshots() throws Exception {
        String systemSource = new ClassPathResource(PromptConstant.INTERVIEW_PLAN_SYSTEM_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);
        String userSource = new ClassPathResource(PromptConstant.INTERVIEW_PLAN_USER_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);
        String styleInstruction = new InterviewerStyleInstructionProvider()
                .instructionFor(InterviewerStyle.PROFESSIONAL);

        Map<String, Object> params = Map.of(
                "templateSnapshot", "{\"title\":\"Backend Java\"}",
                "profileSnapshot", "{\"name\":\"Minh\"}",
                "languageCode", "vi",
                "durationMinutes", 30,
                "durationSeconds", 1800,
                "interviewerStyle", "PROFESSIONAL",
                "styleInstruction", styleInstruction,
                "format", "Return valid JSON");

        String systemPrompt = new PromptTemplate(systemSource).render(params);
        String userPrompt = new PromptTemplate(userSource).render(params);

        assertThat(systemPrompt)
                .contains("Selected interviewer style: PROFESSIONAL")
                .contains("Use a neutral, concise, and structured tone")
                .contains("Ask precise follow-ups")
                .contains("must not change focus-area selection")
                .doesNotContain("{styleInstruction}")
                .doesNotContain("{\"name\":\"Minh\"}");
        assertThat(userPrompt)
                .contains("{\"title\":\"Backend Java\"}")
                .contains("{\"name\":\"Minh\"}")
                .contains("languageCode: vi")
                .contains("Return valid JSON")
                .doesNotContain("{templateSnapshot}");
    }
}
