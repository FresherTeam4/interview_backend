package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.enums.CandidateIntent;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.model.InterviewTemplateSnapshot;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewScoringContextLoaderTest {
    @Test
    void loadsJobFocusAreasAndOnlyCompletedConversationTurns() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewFocusAreaRepository focusAreas = mock(InterviewFocusAreaRepository.class);
        InterviewTurnRepository turns = mock(InterviewTurnRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        InterviewTemplateSnapshot template = new InterviewTemplateSnapshot(
                "v1", 101L, 1, "Backend", "Java Developer",
                "Junior", "v1", null, "Build Java APIs");
        InterviewSession session = InterviewSession.builder()
                .id(501L)
                .status(InterviewSessionStatus.SCORING)
                .languageCode("vi")
                .endReason(InterviewEndReason.CANDIDATE_FINISHED)
                .startedAt(Instant.parse("2026-09-07T01:00:00Z"))
                .endedAt(Instant.parse("2026-09-07T01:20:00Z"))
                .templateSnapshotJson(objectMapper.writeValueAsString(template))
                .jobContextSummary("Backend role")
                .conversationSummary("Candidate discussed APIs")
                .build();
        InterviewFocusArea area = InterviewFocusArea.builder()
                .id(21L)
                .session(session)
                .code("BACKEND")
                .name("Backend")
                .description("Java and Spring")
                .priority(InterviewFocusPriority.HIGH)
                .reason("Core requirement")
                .evidenceStatus(InterviewEvidenceStatus.PARTIAL)
                .evidenceSummary("Some REST evidence")
                .build();
        InterviewTurn opening = turn(
                10L, session, 0, InterviewTurnRole.INTERVIEWER,
                InterviewTurnAction.OPENING, null);
        InterviewTurn completedAnswer = turn(
                11L, session, 1, InterviewTurnRole.CANDIDATE,
                null, InterviewTurnProcessingStatus.COMPLETED);
        InterviewTurn failedAnswer = turn(
                12L, session, 2, InterviewTurnRole.CANDIDATE,
                null, InterviewTurnProcessingStatus.FAILED);
        when(sessions.findById(501L)).thenReturn(Optional.of(session));
        when(focusAreas.findBySessionIdOrderByDisplayOrderAsc(501L))
                .thenReturn(List.of(area));
        when(turns.findBySessionIdOrderByTurnIndexAsc(501L))
                .thenReturn(List.of(opening, completedAnswer, failedAnswer));

        InterviewScoringContext context = new InterviewScoringContextLoader(
                sessions, focusAreas, turns, objectMapper).load(501L);

        assertThat(context.actualDurationSeconds()).isEqualTo(1200);
        assertThat(context.jobTemplate().jobDescriptionText())
                .isEqualTo("Build Java APIs");
        assertThat(context.focusAreas()).singleElement().satisfies(focus -> {
            assertThat(focus.id()).isEqualTo(21L);
            assertThat(focus.code()).isEqualTo("BACKEND");
        });
        assertThat(context.turns()).extracting(InterviewScoringContext.Turn::id)
                .containsExactly(10L, 11L);
    }

    private InterviewTurn turn(
            Long id,
            InterviewSession session,
            int index,
            InterviewTurnRole role,
            InterviewTurnAction action,
            InterviewTurnProcessingStatus processingStatus) {
        return InterviewTurn.builder()
                .id(id)
                .session(session)
                .turnIndex(index)
                .role(role)
                .contentText(role == InterviewTurnRole.CANDIDATE
                        ? "Tôi đã xây dựng REST API." : "Hãy mô tả dự án của bạn.")
                .candidateIntent(role == InterviewTurnRole.CANDIDATE
                        ? CandidateIntent.ANSWER : null)
                .action(action)
                .processingStatus(processingStatus)
                .createdAt(Instant.parse("2026-09-07T01:00:00Z"))
                .build();
    }
}
