package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewReplyResult;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.interview.impl.InterviewConversationEngineImpl;
import com.baseProject.myBaseProject.interview.model.CandidateProfileSnapshot;
import com.baseProject.myBaseProject.interview.model.InterviewContext;
import com.baseProject.myBaseProject.interview.model.InterviewTemplateSnapshot;
import com.baseProject.myBaseProject.interview.model.InterviewTurnContext;
import com.baseProject.myBaseProject.interview.support.InterviewerStyleInstructionProvider;
import com.baseProject.myBaseProject.interview.validation.InterviewReplyValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
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
    void suppliesTimeStyleSnapshotsAndRecentTurnsToAi() throws Exception {
        AiService aiService = mock(AiService.class);
        InterviewConversationEngineImpl engine = new InterviewConversationEngineImpl(
                aiService,
                new ObjectMapper(),
                new InterviewReplyValidator(),
                new InterviewerStyleInstructionProvider());
        when(aiService.generateStructured(any(), any(), any(), eq(InterviewReplyResult.class)))
                .thenReturn(new InterviewReplyResult(
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
                        null,
                        null)),
                30);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(aiService).generateStructured(
                any(), any(), params.capture(), eq(InterviewReplyResult.class));
        assertThat(params.getValue())
                .containsEntry("languageCode", "vi")
                .containsEntry("remainingSeconds", 30L)
                .containsEntry("mustClose", true)
                .containsEntry("interviewerStyle", "FRIENDLY");
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
                new InterviewTemplateSnapshot(
                        "v1", 101L, 1, "Backend", "Java Developer",
                        "Junior", "v1", null, "Build APIs"),
                new CandidateProfileSnapshot(
                        "v1", 35L, 1, "Minh", "Backend Developer",
                        "Java developer", BigDecimal.ONE, "Java Developer",
                        "Junior", List.of(), List.of(), List.of()),
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
