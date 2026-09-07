package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewScoringRecoveryServiceImplTest {
    private static final Instant STARTED_AT = Instant.parse("2026-09-07T08:00:00Z");

    @Test
    void marksScoringLeftByPreviousProcessAsFailed() {
        InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
        InterviewSessionTransitionRecorder transitions =
                mock(InterviewSessionTransitionRecorder.class);
        InterviewSession session = InterviewSession.builder()
                .id(501L)
                .status(InterviewSessionStatus.SCORING)
                .endedAt(STARTED_AT.minusSeconds(10))
                .updatedAt(STARTED_AT.minusSeconds(10))
                .build();
        when(sessions.findByStatusAndEndedAtBefore(
                InterviewSessionStatus.SCORING, STARTED_AT))
                .thenReturn(List.of(session));

        int recovered = new InterviewScoringRecoveryServiceImpl(
                sessions,
                transitions,
                Clock.fixed(STARTED_AT.plusSeconds(1), ZoneOffset.UTC))
                .failInterruptedScoring(STARTED_AT);

        assertThat(recovered).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(InterviewSessionStatus.SCORING_FAILED);
        assertThat(session.getScoringErrorCode()).isEqualTo("INTERVIEW_SCORING_FAILED");
    }
}
