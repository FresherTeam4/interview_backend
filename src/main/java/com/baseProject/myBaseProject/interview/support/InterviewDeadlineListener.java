package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.interview.InterviewDeadlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewDeadlineListener {
    private final InterviewDeadlineService deadlineService;

    @Scheduled(fixedDelayString = "${app.interview-session.deadline-sweep-ms:30000}")
    public void closeExpiredSessions() {
        int closed = deadlineService.closeExpiredSessions();
        if (closed > 0) {
            log.info("Closed {} interview sessions after their deadline", closed);
        }
    }
}
