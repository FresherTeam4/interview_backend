package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewContextLoaderTest {

    @Test
    void ownedLoaderDoesNotFallBackToAnUnscopedSessionLookup() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        InterviewContextLoader loader = new InterviewContextLoader(
                sessions, focusAreas, new ObjectMapper());
        when(sessions.findByIdAndUserId(501L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loader.loadOwned(7L, 501L))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        verify(sessions, never()).findById(501L);
    }

    @Test
    void loadsOwnedAiContextWithImmutableSnapshotsAndSessionState() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        InterviewContextLoader loader = new InterviewContextLoader(
                sessions, focusAreas, new ObjectMapper());

        InterviewSession session = InterviewSession.builder()
                .id(501L)
                .status(InterviewSessionStatus.READY)
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .templateSnapshotJson("""
                        {
                          "snapshotSchemaVersion": "v1",
                          "templateId": 101,
                          "templateVersion": 2,
                          "title": "Backend Java",
                          "jobDescriptionText": "Build Java APIs"
                        }
                        """)
                .profileSnapshotJson("""
                        {
                          "snapshotSchemaVersion": "v1",
                          "profileId": 35,
                          "profileVersion": 4,
                          "name": "Minh",
                          "skills": [],
                          "educations": [],
                          "projects": []
                        }
                        """)
                .jobContextSummary("Backend Java role")
                .candidateContextSummary("Spring Boot experience")
                .openingMessage("Chào bạn")
                .conversationSummary("Đã thảo luận Java core")
                .startedAt(Instant.parse("2026-09-06T08:00:00Z"))
                .deadlineAt(Instant.parse("2026-09-06T08:30:00Z"))
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

        when(sessions.findByIdAndUserId(501L, 7L)).thenReturn(Optional.of(session));
        when(focusAreas.findBySessionIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(area));

        var context = loader.loadOwned(7L, 501L);

        assertThat(context.status()).isEqualTo(InterviewSessionStatus.READY);
        assertThat(context.template().title()).isEqualTo("Backend Java");
        assertThat(context.template().jobDescriptionText()).isEqualTo("Build Java APIs");
        assertThat(context.candidate().name()).isEqualTo("Minh");
        assertThat(context.jobContextSummary()).isEqualTo("Backend Java role");
        assertThat(context.candidateContextSummary()).isEqualTo("Spring Boot experience");
        assertThat(context.deadlineAt())
                .isEqualTo(Instant.parse("2026-09-06T08:30:00Z"));
        assertThat(context.currentTurnIndex()).isEqualTo(3);
        assertThat(context.focusAreas()).singleElement().satisfies(focusArea -> {
            assertThat(focusArea.code()).isEqualTo("JAVA");
            assertThat(focusArea.evidenceStatus()).isEqualTo(InterviewEvidenceStatus.PARTIAL);
            assertThat(focusArea.evidenceSummary()).isEqualTo("Ứng viên hiểu OOP");
        });
    }
}
