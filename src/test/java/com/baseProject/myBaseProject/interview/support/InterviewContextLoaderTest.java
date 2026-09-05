package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewContextLoaderTest {

    @Test
    void loadsInternalAiContextFromPersistedSessionData() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        InterviewContextLoader loader = new InterviewContextLoader(sessions, focusAreas);

        InterviewSession session = InterviewSession.builder()
                .id(501L)
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .jobContextSummary("Backend Java role")
                .candidateContextSummary("Spring Boot experience")
                .openingMessage("Chào bạn")
                .conversationSummary("Đã thảo luận Java core")
                .currentTurnIndex(3)
                .build();
        InterviewFocusArea area = InterviewFocusArea.builder()
                .code("JAVA")
                .name("Java")
                .description("Kiểm tra kiến thức Java")
                .priority(InterviewFocusPriority.HIGH)
                .reason("Yêu cầu chính của JD")
                .plannedSeconds(300)
                .evidenceStatus(InterviewEvidenceStatus.PARTIAL)
                .evidenceSummary("Ứng viên hiểu OOP")
                .displayOrder((short) 0)
                .build();

        when(sessions.findById(501L)).thenReturn(Optional.of(session));
        when(focusAreas.findBySessionIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(area));

        var context = loader.load(501L);

        assertThat(context.jobContextSummary()).isEqualTo("Backend Java role");
        assertThat(context.candidateContextSummary()).isEqualTo("Spring Boot experience");
        assertThat(context.currentTurnIndex()).isEqualTo(3);
        assertThat(context.focusAreas()).singleElement().satisfies(focusArea -> {
            assertThat(focusArea.code()).isEqualTo("JAVA");
            assertThat(focusArea.evidenceStatus()).isEqualTo(InterviewEvidenceStatus.PARTIAL);
            assertThat(focusArea.evidenceSummary()).isEqualTo("Ứng viên hiểu OOP");
        });
    }
}
