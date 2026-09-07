package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.interview.InterviewScoringService;
import com.baseProject.myBaseProject.interview.model.InterviewScoringRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InterviewScoringRequestedListenerTest {
    @Test
    void dispatchesScoringAfterReceivingEvent() {
        InterviewScoringService service = mock(InterviewScoringService.class);

        new InterviewScoringRequestedListener(service)
                .onScoringRequested(new InterviewScoringRequestedEvent(501L));

        verify(service).scoreAsync(501L);
    }

    @Test
    void recordsFailureWhenExecutorRejectsWork() {
        InterviewScoringService service = mock(InterviewScoringService.class);
        doThrow(new TaskRejectedException("queue full"))
                .when(service).scoreAsync(501L);

        new InterviewScoringRequestedListener(service)
                .onScoringRequested(new InterviewScoringRequestedEvent(501L));

        verify(service).markDispatchFailed(501L);
    }
}
