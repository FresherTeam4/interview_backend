package com.baseProject.myBaseProject.interview.support;

import com.baseProject.myBaseProject.entity.InterviewFocusArea;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.interview.model.InterviewScoringContext;
import com.baseProject.myBaseProject.interview.model.InterviewTemplateSnapshot;
import com.baseProject.myBaseProject.repository.InterviewFocusAreaRepository;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InterviewScoringContextLoader {
    private final InterviewSessionRepository sessions;
    private final InterviewFocusAreaRepository focusAreas;
    private final InterviewTurnRepository turns;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public InterviewScoringContext load(Long sessionId) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND));

        return new InterviewScoringContext(
                session.getId(),
                session.getStatus(),
                session.getLanguageCode(),
                session.getEndReason(),
                actualDurationSeconds(session.getStartedAt(), session.getEndedAt()),
                objectMapper.readValue(
                        session.getTemplateSnapshotJson(), InterviewTemplateSnapshot.class),
                session.getJobContextSummary(),
                session.getConversationSummary(),
                focusAreas.findBySessionIdOrderByDisplayOrderAsc(sessionId).stream()
                        .map(this::toFocusArea)
                        .toList(),
                turns.findBySessionIdOrderByTurnIndexAsc(sessionId).stream()
                        // Không chấm candidate turn đang PROCESSING hoặc FAILED vì chưa thành hội thoại hợp lệ.
                        .filter(this::isCompletedConversationTurn)
                        .map(this::toTurn)
                        .toList());
    }

    private boolean isCompletedConversationTurn(InterviewTurn turn) {
        return turn.getRole() == InterviewTurnRole.INTERVIEWER
                || turn.getProcessingStatus() == InterviewTurnProcessingStatus.COMPLETED;
    }

    private long actualDurationSeconds(Instant startedAt, Instant endedAt) {
        // Timestamp thiếu hoặc đảo thứ tự không được tạo duration âm trong prompt.
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return 0;
        }
        return Duration.between(startedAt, endedAt).toSeconds();
    }

    private InterviewScoringContext.FocusArea toFocusArea(InterviewFocusArea area) {
        return new InterviewScoringContext.FocusArea(
                area.getId(),
                area.getCode(),
                area.getName(),
                area.getDescription(),
                area.getPriority(),
                area.getReason(),
                area.getEvidenceStatus(),
                area.getEvidenceSummary());
    }

    private InterviewScoringContext.Turn toTurn(InterviewTurn turn) {
        return new InterviewScoringContext.Turn(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getRole(),
                turn.getContentText(),
                turn.getCandidateIntent(),
                turn.getAction(),
                turn.getFocusAreaCode());
    }
}
