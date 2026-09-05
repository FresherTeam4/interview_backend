package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.dto.ai.interview.InterviewPlanResult;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewPlanningService;
import com.baseProject.myBaseProject.interview.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewPreparationServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");

    @Test
    void persistsFocusAreasBeforeMarkingSessionReady() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        InterviewPlanningService planning = mock(InterviewPlanningService.class);
        InterviewSessionTransitionRecorder transitions =
                mock(InterviewSessionTransitionRecorder.class);
        InterviewSession session = preparingSession();
        when(sessions.findByIdForUpdate(501L)).thenReturn(Optional.of(session));
        when(planning.generate("{\"template\":1}", "{\"profile\":2}",
                "vi", 30, InterviewerStyle.PROFESSIONAL)).thenReturn(plan());

        service(sessions, focusAreas, planning, transitions).prepareAsync(501L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.READY);
        assertThat(session.getPreparedAt()).isEqualTo(NOW);
        assertThat(session.getOpeningMessage()).startsWith("Chào Minh");
        verify(focusAreas).saveAll(org.mockito.ArgumentMatchers.argThat(areas -> {
            var list = (List<?>) areas;
            return list.size() == 2;
        }));
        verify(transitions).record(
                session, InterviewSessionStatus.PREPARING, InterviewSessionStatus.READY,
                "Interview plan prepared",
                com.baseProject.myBaseProject.enums.InterviewTransitionActor.SYSTEM, NOW);
    }

    @Test
    void preservesRetryableAiFailureOnSession() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        InterviewPlanningService planning = mock(InterviewPlanningService.class);
        InterviewSessionTransitionRecorder transitions =
                mock(InterviewSessionTransitionRecorder.class);
        InterviewSession session = preparingSession();
        when(sessions.findByIdForUpdate(501L)).thenReturn(Optional.of(session));
        when(planning.generate(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenThrow(new DomainException(ErrorCode.AI_TIMEOUT));

        service(sessions, focusAreas, planning, transitions).prepareAsync(501L);

        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.PREPARATION_FAILED);
        assertThat(session.getPreparationErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(session.getPreparationErrorMessage())
                .isEqualTo(ErrorCode.AI_TIMEOUT.getDefaultMessage());
    }

    private InterviewPreparationServiceImpl service(
            InterviewSessionRepository sessions,
            InterviewFocusAreaRepository focusAreas,
            InterviewPlanningService planning,
            InterviewSessionTransitionRecorder transitions) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(null);
        return new InterviewPreparationServiceImpl(
                sessions, focusAreas, planning, transitions, chatModel,
                Clock.fixed(NOW, ZoneOffset.UTC), transactionManager());
    }

    private InterviewSession preparingSession() {
        return InterviewSession.builder()
                .id(501L)
                .status(InterviewSessionStatus.PREPARING)
                .templateSnapshotJson("{\"template\":1}")
                .profileSnapshotJson("{\"profile\":2}")
                .languageCode("vi")
                .durationMinutes(30)
                .interviewerStyle(InterviewerStyle.PROFESSIONAL)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .build();
    }

    private InterviewPlanResult plan() {
        return new InterviewPlanResult(
                "vi", "Backend role", "Spring candidate",
                List.of(
                        area("JAVA_SPRING", 480),
                        area("DATABASE", 360)),
                "Chào Minh, hãy giới thiệu về kinh nghiệm phù hợp nhất của bạn.");
    }

    private InterviewPlanResult.FocusArea area(String code, int seconds) {
        return new InterviewPlanResult.FocusArea(
                code, code, "Evidence target", InterviewFocusPriority.HIGH,
                "Relevant to both JD and CV", seconds);
    }

    private PlatformTransactionManager transactionManager() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }
}
