package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.InterviewPreparationRecoveryService;
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
public class InterviewPreparationRecoveryServiceImpl
        implements InterviewPreparationRecoveryService {
    private final InterviewSessionRepository sessions;
    private final InterviewSessionTransitionRecorder transitionRecorder;
    private final Clock clock;

    @Override
    @Transactional
    public int failInterruptedPreparations(Instant applicationStartedAt) {
        // Chỉ recovery các session đã tồn tại trước lần khởi động hiện tại.
        List<InterviewSession> interrupted = sessions.findByStatusAndCreatedAtBefore(
                InterviewSessionStatus.PREPARING, applicationStartedAt);
        Instant now = clock.instant();
        for (InterviewSession session : interrupted) {
            session.markPreparationFailed(
                    ErrorCode.INTERVIEW_SESSION_PREPARATION_FAILED.name(),
                    Message.INTERVIEW_SESSION_PREPARATION_FAILED, now);
            transitionRecorder.record(
                    session, InterviewSessionStatus.PREPARING,
                    InterviewSessionStatus.PREPARATION_FAILED,
                    "Preparation was interrupted by a server restart",
                    InterviewTransitionActor.SCHEDULER, now);
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted interview preparations as failed", interrupted.size());
        }
        return interrupted.size();
    }
}
