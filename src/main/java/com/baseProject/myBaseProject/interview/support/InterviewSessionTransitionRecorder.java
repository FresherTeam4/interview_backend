package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewSessionTransition;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTransitionActor;
import com.baseProject.myBaseProject.repository.InterviewSessionTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InterviewSessionTransitionRecorder {
    private final InterviewSessionTransitionRepository transitions;

    public void record(
            InterviewSession session,
            InterviewSessionStatus from,
            InterviewSessionStatus to,
            String reason,
            InterviewTransitionActor actor,
            Instant occurredAt) {

        // Mỗi lần đổi trạng thái tạo bản ghi mới để giữ nguyên lịch sử audit của session.
        transitions.save(InterviewSessionTransition.builder()
                .session(session)
                .fromStatus(from)
                .toStatus(to)
                .reason(reason)
                .actor(actor)
                .occurredAt(occurredAt)
                .build());
    }
}
