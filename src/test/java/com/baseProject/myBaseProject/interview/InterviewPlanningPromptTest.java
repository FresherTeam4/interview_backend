package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.constant.PromptConstant;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewPlanningPromptTest {
    @Test
    void promptRendersSnapshotsAndStructuredOutputContract() throws Exception {
        String source = new ClassPathResource(PromptConstant.INTERVIEW_PLAN_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);

        String rendered = new PromptTemplate(source).render(Map.of(
                "templateSnapshot", "{\"title\":\"Backend Java\"}",
                "profileSnapshot", "{\"name\":\"Minh\"}",
                "languageCode", "vi",
                "durationMinutes", 30,
                "durationSeconds", 1800,
                "interviewerStyle", "PROFESSIONAL",
                "format", "Return valid JSON"));

        assertThat(rendered)
                .contains("{\"title\":\"Backend Java\"}")
                .contains("{\"name\":\"Minh\"}")
                .contains("languageCode: vi")
                .contains("Return valid JSON")
                .doesNotContain("{templateSnapshot}");
    }
}
