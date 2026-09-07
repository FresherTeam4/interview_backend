package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewScoringRecoveryService;
import com.baseProject.myBaseProject.interview.support.InterviewSessionTransitionRecorder;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewScoringRecoveryServiceImpl
        implements InterviewScoringRecoveryService {
    private final InterviewSessionRepository sessions;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final Clock clock;

    @Override
    @Transactional
    public int failInterruptedScoring(Instant applicationStartedAt) {
        // Session còn SCORING từ tiến trình cũ cần được mở cho user retry.
        List<InterviewSession> interrupted = sessions.findByStatusAndEndedAtBefore(
                InterviewSessionStatus.SCORING, applicationStartedAt);
        Instant now = clock.instant();
        for (InterviewSession session : interrupted) {
            session.markScoringFailed(
                    ErrorCode.INTERVIEW_SCORING_FAILED.name(),
                    Message.INTERVIEW_SCORING_FAILED,
                    now);
            transitionRecorder.record(
                    session,
                    InterviewSessionStatus.SCORING,
                    InterviewSessionStatus.SCORING_FAILED,
                    "Scoring was interrupted by a server restart",
                    InterviewTransitionActor.SCHEDULER,
                    now);
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted interview scoring jobs as failed",
                    interrupted.size());
        }

        return interrupted.size();
    }
}
