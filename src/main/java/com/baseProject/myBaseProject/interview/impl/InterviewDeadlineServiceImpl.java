package com.baseProject.myBaseProject.interview.impl;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.interview.InterviewDeadlineService;
import com.baseProject.myBaseProject.interview.support.InterviewSessionCloser;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class InterviewDeadlineServiceImpl implements InterviewDeadlineService {
    private final InterviewSessionRepository sessions;
    private final InterviewSessionCloser sessionCloser;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public InterviewDeadlineServiceImpl(
            InterviewSessionRepository sessions,
            InterviewSessionCloser sessionCloser,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.sessionCloser = sessionCloser;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public int closeExpiredSessions() {
        Instant deadline = clock.instant();
        List<Long> sessionIds = sessions
                .findTop100ByStatusAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
                        InterviewSessionStatus.IN_PROGRESS, deadline)
                .stream()
                .map(InterviewSession::getId)
                .toList();
        int closed = 0;
        // Mỗi session dùng transaction riêng để không giữ khóa cho cả batch quét deadline.
        for (Long sessionId : sessionIds) {
            Boolean changed = transactions.execute(status -> closeIfExpired(sessionId));
            if (Boolean.TRUE.equals(changed)) {
                closed++;
            }
        }

        return closed;
    }

    private boolean closeIfExpired(Long sessionId) {
        InterviewSession session = sessions.findByIdForUpdate(sessionId).orElse(null);
        Instant now = clock.instant();
        if (session == null
                || session.getStatus() != InterviewSessionStatus.IN_PROGRESS
                || session.getDeadlineAt() == null
                || session.getDeadlineAt().isAfter(now)) {

                return false;
        }
        sessionCloser.close(
                session,
                InterviewEndReason.TIME_EXPIRED,
                InterviewTransitionActor.SCHEDULER,
                now);

        return true;
    }
}
