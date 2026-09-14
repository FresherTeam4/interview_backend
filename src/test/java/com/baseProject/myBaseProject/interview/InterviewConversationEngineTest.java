package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.impl.InterviewConversationEngineImpl;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.model.InterviewTurnContext;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import com.baseProject.myBaseProject.interview.validation.InterviewReplyValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewConversationEngineTest {
    @Test
    void suppliesTimeStyleSummariesAndRecentTurnsWithoutSnapshots() throws Exception {
        AiService aiService = mock(AiService.class);
        InterviewConversationEngineImpl engine = new InterviewConversationEngineImpl(
                aiService,
                new ObjectMapper(),
                new InterviewReplyValidator(),
                new InterviewerStyleInstructionProvider());
        when(aiService.generateStructured(any(), any(), any(), eq(InterviewReplyResult.class)))
                .thenReturn(new InterviewReplyResult(
                        CandidateIntent.ANSWER,
                        InterviewTurnAction.CLOSE,
                        "Cảm ơn bạn, buổi phỏng vấn kết thúc tại đây.",
                        null,
                        "Ứng viên đã trình bày kinh nghiệm backend.",
                        List.of()));

        InterviewReplyResult result = engine.reply(
                context(),
                List.of(new InterviewTurnContext(
                        1,
                        InterviewTurnRole.CANDIDATE,
                        "Tôi đã xây dựng REST API.",
                        CandidateIntent.ANSWER,
                        null,
                        null)),
                30);

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(aiService).generateStructured(
                systemPrompt.capture(), userPrompt.capture(), params.capture(),
                eq(InterviewReplyResult.class));
        assertThat(systemPrompt.getValue())
                .contains("no more than about 45 words")
                .contains("Ask exactly one primary question at a time")
                .contains("This overrides every intent rule below")
                .contains("complete replacement summary for the session");
        assertThat(params.getValue())
                .containsEntry("languageCode", "vi")
                .containsEntry("remainingSeconds", 30L)
                .containsEntry("mustClose", true)
                .containsEntry("interviewerStyle", "FRIENDLY")
                .containsEntry("jobSummary", "Backend Java role")
                .containsEntry("candidateSummary", "Candidate has Java experience")
                .doesNotContainKeys("templateSnapshot", "candidateSnapshot");
        assertThat(userPrompt.getValue())
                .contains("Prepared job summary", "Prepared candidate summary")
                .doesNotContain("templateSnapshot", "candidateSnapshot");
        assertThat((String) params.getValue().get("recentTurns"))
                .contains("REST API", "CANDIDATE");
        assertThat(result.action()).isEqualTo(InterviewTurnAction.CLOSE);
    }

    private InterviewContext context() {
        return new InterviewContext(
                501L,
                InterviewSessionStatus.IN_PROGRESS,
                "vi",
                30,
                InterviewerStyle.FRIENDLY,
                "Backend Java role",
                "Candidate has Java experience",
                "Xin chào",
                null,
                Instant.parse("2026-09-06T08:00:00Z"),
                Instant.parse("2026-09-06T08:30:00Z"),
                1,
                List.of(new InterviewContext.FocusArea(
                        "BACKEND", "Backend", "Spring", InterviewFocusPriority.HIGH,
                        "Core requirement", 300, InterviewEvidenceStatus.PARTIAL,
                        "Some API experience", (short) 0)));
    }
}
