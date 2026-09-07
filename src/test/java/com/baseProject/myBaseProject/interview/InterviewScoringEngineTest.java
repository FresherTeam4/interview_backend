package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.dto.ai.interview.InterviewAssessmentResult;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewAssessmentConfidence;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.interview.impl.InterviewScoringEngineImpl;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.model.InterviewTemplateSnapshot;
import com.baseProject.myBaseProject.interview.validation.InterviewAssessmentValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewScoringEngineTest {
    @Test
    void suppliesJobFocusAreasAndCompletedTranscriptWithoutCandidateProfile() throws Exception {
        AiService aiService = mock(AiService.class);
        InterviewScoringEngineImpl engine = new InterviewScoringEngineImpl(
                aiService,
                new ObjectMapper(),
                new InterviewAssessmentValidator());
        when(aiService.generateStructured(
                any(), any(), any(), eq(InterviewAssessmentResult.class)))
                .thenReturn(validAssessment());

        InterviewAssessmentResult result = engine.assess(context());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(aiService).generateStructured(
                any(), any(), params.capture(), eq(InterviewAssessmentResult.class));
        assertThat(params.getValue())
                .containsEntry("languageCode", "vi")
                .containsEntry("endReason", "AI_COMPLETED")
                .containsEntry("actualDurationSeconds", 1200L)
                .doesNotContainKeys("candidateSnapshot", "candidateSummary",
                        "interviewerStyle");
        assertThat((String) params.getValue().get("jobTemplate"))
                .contains("Build Java APIs");
        assertThat((String) params.getValue().get("turns"))
                .contains("CANDIDATE", "REST API", "ANSWER");
        assertThat(result.focusAreaAssessments()).singleElement();
    }

    private InterviewAssessmentResult validAssessment() {
        return new InterviewAssessmentResult(
                "Ứng viên thể hiện kiến thức backend.",
                List.of(new InterviewAssessmentResult.FocusAreaAssessment(
                        "BACKEND",
                        75,
                        InterviewAssessmentConfidence.HIGH,
                        InterviewEvidenceStatus.SUFFICIENT,
                        "Có ví dụ triển khai REST API.",
                        List.of("Hiểu Spring Boot"),
                        List.of("Thiếu số liệu tải"),
                        "Nên bổ sung kết quả định lượng.",
                        List.of(11L))),
                70,
                "Câu trả lời rõ ràng.",
                List.of(new InterviewAssessmentResult.ReportItem(
                        "Nắm backend", "Có ví dụ thực tế.", List.of(11L))),
                List.of(),
                List.of());
    }

    private InterviewScoringContext context() {
        return new InterviewScoringContext(
                501L,
                InterviewSessionStatus.SCORING,
                "vi",
                InterviewEndReason.AI_COMPLETED,
                1200,
                new InterviewTemplateSnapshot(
                        "v1", 101L, 1, "Backend", "Java Developer",
                        "Junior", "v1", null, "Build Java APIs"),
                "Backend Java role",
                "Ứng viên đã mô tả REST API.",
                List.of(new InterviewScoringContext.FocusArea(
                        21L, "BACKEND", "Backend", "Java and Spring",
                        InterviewFocusPriority.HIGH, "Core requirement",
                        InterviewEvidenceStatus.SUFFICIENT, "REST API evidence")),
                List.of(
                        new InterviewScoringContext.Turn(
                                10L, 0, InterviewTurnRole.INTERVIEWER,
                                "Bạn đã xây dựng gì?", null,
                                InterviewTurnAction.OPENING, null),
                        new InterviewScoringContext.Turn(
                                11L, 1, InterviewTurnRole.CANDIDATE,
                                "Tôi đã xây dựng REST API.", CandidateIntent.ANSWER,
                                null, null)));
    }
}
