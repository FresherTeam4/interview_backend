package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.interview.InterviewScoringService;
import com.baseProject.myBaseProject.interview.model.InterviewScoringRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InterviewScoringRequestedListener {
    private final InterviewScoringService scoringService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onScoringRequested(InterviewScoringRequestedEvent event) {
        try {
            // Với event trong transaction, chỉ dispatch sau khi trạng thái SCORING đã commit.
            scoringService.scoreAsync(event.sessionId());
        } catch (TaskRejectedException exception) {
            // Queue đầy phải tạo trạng thái có thể retry thay vì để session treo ở SCORING.
            scoringService.markDispatchFailed(event.sessionId());
        }
    }
}
