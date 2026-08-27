package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionSummaryResponse;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.enums.TurnRole;
import com.baseProject.myBaseProject.repository.projection.SessionSummaryProjection;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterviewSessionMapper {

    public InterviewSessionAcceptedResponse toAcceptedResponse(InterviewSession session) {
        return new InterviewSessionAcceptedResponse(
                session.getId(),
                session.getStatus(),
                session.getAwaitingAction(),
                session.getVersion(),
                session.getCreatedAt());
    }

    public InterviewSessionResponse toResponse(
            InterviewSession session,
            List<SessionTurn> turns) {
        List<InterviewSessionResponse.Turn> turnResponses = turns.stream()
                .map(this::toTurn)
                .toList();
        return new InterviewSessionResponse(
                session.getId(),
                new InterviewSessionResponse.ProfileReference(
                        session.getProfile().getId(),
                        session.getProfile().getHeadline()),
                new InterviewSessionResponse.JobDescriptionReference(
                        session.getJobDescription().getId(),
                        session.getJobDescription().getTitle()),
                session.getDifficulty(),
                session.getMode(),
                session.getLanguageCode(),
                session.getStatus(),
                session.getAwaitingAction(),
                session.getVersion(),
                session.getAnsweredQuestionCount(),
                session.getTotalQuestionCount(),
                currentPrompt(session, turns),
                turnResponses,
                null,
                session.getStatusMessage(),
                session.getLastActivityAt(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    public InterviewSessionSummaryResponse toSummaryResponse(SessionSummaryProjection summary) {
        return new InterviewSessionSummaryResponse(
                summary.id(),
                summary.profileId(),
                summary.profileHeadline(),
                summary.jobDescriptionId(),
                summary.jobDescriptionTitle(),
                summary.difficulty(),
                summary.mode(),
                summary.status(),
                summary.awaitingAction(),
                summary.answeredQuestionCount(),
                summary.totalQuestionCount(),
                summary.overallScore(),
                summary.lastActivityAt(),
                summary.createdAt());
    }

    private InterviewSessionResponse.CurrentPrompt currentPrompt(
            InterviewSession session,
            List<SessionTurn> turns) {
        if (session.getCurrentQuestionOrdinal() == null) {
            return null;
        }
        return turns.stream()
                .filter(turn -> turn.getRole() == TurnRole.INTERVIEWER)
                .filter(turn -> turn.getQuestion() != null)
                .reduce((first, second) -> second)
                .map(turn -> new InterviewSessionResponse.CurrentPrompt(
                        turn.getId(),
                        turn.getQuestion().getId(),
                        turn.getQuestion().getOrdinal(),
                        turn.getContentText(),
                        turn.isFollowUp(),
                        turn.getFollowUpDepth(),
                        null))
                .orElse(null);
    }

    private InterviewSessionResponse.Turn toTurn(SessionTurn turn) {
        return new InterviewSessionResponse.Turn(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getRole(),
                turn.getInputMode(),
                turn.getContentText(),
                turn.isFollowUp(),
                turn.getFollowUpDepth(),
                turn.getCreatedAt());
    }
}
