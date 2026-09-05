package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import com.baseProject.myBaseProject.interview.validation.InterviewPlanValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewPlanningServiceImplTest {

    @Test
    void injectsSelectedStyleInstructionIntoPlanningPrompt() throws Exception {
        AiService aiService = mock(AiService.class);
        InterviewPlanValidator validator = mock(InterviewPlanValidator.class);
        InterviewerStyleInstructionProvider styleInstructions =
                mock(InterviewerStyleInstructionProvider.class);
        InterviewPlanResult plan = new InterviewPlanResult(
                "vi", "Job", "Candidate", List.of(), "Xin chào");

        when(styleInstructions.instructionFor(InterviewerStyle.CHALLENGING))
                .thenReturn("Challenge unsupported assumptions respectfully");
        when(aiService.generateStructured(
                anyString(), anyString(), org.mockito.ArgumentMatchers.anyMap(),
                eq(InterviewPlanResult.class)))
                .thenReturn(plan);
        when(validator.validate(plan, "vi", 30)).thenReturn(plan);

        InterviewPlanningServiceImpl service = new InterviewPlanningServiceImpl(
                aiService, validator, styleInstructions);

        assertThat(service.generate(
                "{\"template\":1}", "{\"profile\":2}", "vi", 30,
                InterviewerStyle.CHALLENGING)).isSameAs(plan);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(aiService).generateStructured(
                anyString(), anyString(), params.capture(), eq(InterviewPlanResult.class));
        assertThat(params.getValue())
                .containsEntry("interviewerStyle", "CHALLENGING")
                .containsEntry(
                        "styleInstruction",
                        "Challenge unsupported assumptions respectfully");
    }
}
