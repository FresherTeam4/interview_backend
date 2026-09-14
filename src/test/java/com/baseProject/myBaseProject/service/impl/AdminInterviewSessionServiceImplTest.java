package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionTransitionRepository;
import com.baseProject.myBaseProject.service.InterviewReportService;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminInterviewSessionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Test
    void returnsOperationalMetadataAndOrderedTransitions() {
        Fixture fixture = new Fixture();
        InterviewSession session = fixture.session(InterviewSessionStatus.SCORING_FAILED);
        InterviewSessionTransition transition = InterviewSessionTransition.builder()
                .id(91L)
                .session(session)
                .fromStatus(InterviewSessionStatus.SCORING)
                .toStatus(InterviewSessionStatus.SCORING_FAILED)
                .reason("AI timed out")
                .actor(InterviewTransitionActor.SYSTEM)
                .occurredAt(NOW)
                .build();
        when(fixture.sessions.findByIdForAdmin(501L)).thenReturn(Optional.of(session));
        when(fixture.transitions.findBySessionIdOrderByOccurredAtAsc(501L))
                .thenReturn(List.of(transition));

        var response = fixture.service.get(501L);

        assertThat(response.user().email()).isEqualTo("minh@example.com");
        assertThat(response.planModelName()).isEqualTo("test-model");
        assertThat(response.scoringErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(response.transitions()).singleElement().satisfies(item -> {
            assertThat(item.actor()).isEqualTo(InterviewTransitionActor.SYSTEM);
            assertThat(item.toStatus()).isEqualTo(InterviewSessionStatus.SCORING_FAILED);
        });
    }

    @Test
    void adminRetryDelegatesToSharedPreparationWorkflow() {
        Fixture fixture = new Fixture();
        InterviewSession session = fixture.session(InterviewSessionStatus.PREPARING);
        when(fixture.sessions.findByIdForAdmin(501L)).thenReturn(Optional.of(session));
        when(fixture.transitions.findBySessionIdOrderByOccurredAtAsc(501L))
                .thenReturn(List.of());

        var response = fixture.service.retryPreparation(3L, 501L);

        verify(fixture.interviewSessions).retryPreparationForAdmin(501L);
        assertThat(response.status()).isEqualTo(InterviewSessionStatus.PREPARING);
    }

    private static final class Fixture {
        private final InterviewSessionRepository sessions =
                mock(InterviewSessionRepository.class);
        private final InterviewSessionTransitionRepository transitions =
                mock(InterviewSessionTransitionRepository.class);
        private final InterviewSessionService interviewSessions =
                mock(InterviewSessionService.class);
        private final InterviewReportService reports = mock(InterviewReportService.class);
        private final AdminInterviewSessionServiceImpl service =
                new AdminInterviewSessionServiceImpl(
                        sessions, transitions, interviewSessions, reports);

        private InterviewSession session(InterviewSessionStatus status) {
            return InterviewSession.builder()
                    .id(501L)
                    .user(UserAccount.builder()
                            .id(7L)
                            .fullName("Minh")
                            .email("minh@example.com")
                            .build())
                    .status(status)
                    .templateTitleSnapshot("Backend Java")
                    .profileNameSnapshot("Minh profile")
                    .languageCode("vi")
                    .durationMinutes(30)
                    .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                    .mode(InterviewSessionMode.TURN_BASED)
                    .planSchemaVersion("v1")
                    .planModelName("test-model")
                    .planPromptVersion("v3")
                    .scoringErrorCode("AI_TIMEOUT")
                    .scoringErrorMessage("AI timed out")
                    .createdAt(NOW.minusSeconds(1800))
                    .updatedAt(NOW)
                    .build();
        }
    }
}
