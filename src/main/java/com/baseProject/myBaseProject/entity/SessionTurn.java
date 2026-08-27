package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.TurnInputMode;
import com.baseProject.myBaseProject.enums.TurnRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "session_turns",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_session_turns_session_index",
                        columnNames = {"session_id", "turn_index"}),
                @UniqueConstraint(
                        name = "uq_session_turns_session_client_turn",
                        columnNames = {"session_id", "client_turn_id"})
        },
        indexes = {
                @Index(name = "idx_session_turns_question_id", columnList = "question_id"),
                @Index(name = "idx_session_turns_parent_turn_id", columnList = "parent_turn_id")
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", updatable = false)
    private SessionQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_turn_id", updatable = false)
    private SessionTurn parentTurn;

    @Column(name = "turn_index", nullable = false, updatable = false)
    private int turnIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TurnRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 30, updatable = false)
    private TurnInputMode inputMode;

    @Column(name = "content_text", nullable = false, updatable = false, columnDefinition = "MEDIUMTEXT")
    private String contentText;

    @Column(name = "client_turn_id", length = 64, updatable = false)
    private String clientTurnId;

    @Column(name = "is_followup", nullable = false, updatable = false)
    private boolean followUp;

    @Column(name = "followup_depth", nullable = false, updatable = false)
    private short followUpDepth;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at", updatable = false)
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SessionTurn firstInterviewerPrompt(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            Instant now) {
        return baseQuestionPrompt(session, question, turnIndex, now);
    }

    public static SessionTurn nextBaseQuestionPrompt(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            Instant now) {
        return baseQuestionPrompt(session, question, turnIndex, now);
    }

    public static SessionTurn candidateTextAnswer(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            String content,
            String clientTurnId,
            Instant now) {
        validateQuestionOwnership(session, question);
        if (clientTurnId == null || clientTurnId.isBlank()) {
            throw new IllegalArgumentException("Candidate turn requires a client turn ID");
        }
        SessionTurn turn = new SessionTurn();
        turn.session = Objects.requireNonNull(session);
        turn.question = Objects.requireNonNull(question);
        turn.turnIndex = turnIndex;
        turn.role = TurnRole.CANDIDATE;
        turn.inputMode = TurnInputMode.TEXT;
        turn.contentText = Objects.requireNonNull(content);
        turn.clientTurnId = clientTurnId;
        turn.followUp = false;
        turn.followUpDepth = 0;
        turn.startedAt = Objects.requireNonNull(now);
        turn.endedAt = now;
        turn.createdAt = now;
        return turn;
    }

    public static SessionTurn followUpPrompt(
            InterviewSession session,
            SessionQuestion question,
            SessionTurn parentCandidateTurn,
            int turnIndex,
            String content,
            short followUpDepth,
            int latencyMs,
            Instant now) {
        validateQuestionOwnership(session, question);
        Objects.requireNonNull(parentCandidateTurn);
        if (parentCandidateTurn.getRole() != TurnRole.CANDIDATE
                || !Objects.equals(parentCandidateTurn.getSession().getId(), session.getId())
                || parentCandidateTurn.getQuestion() == null
                || !Objects.equals(parentCandidateTurn.getQuestion().getId(), question.getId())
                || followUpDepth < 1
                || followUpDepth > 2
                || latencyMs < 0
                || content == null
                || content.isBlank()) {
            throw new IllegalArgumentException("Follow-up prompt context is invalid");
        }

        SessionTurn turn = new SessionTurn();
        turn.session = Objects.requireNonNull(session);
        turn.question = Objects.requireNonNull(question);
        turn.parentTurn = parentCandidateTurn;
        turn.turnIndex = turnIndex;
        turn.role = TurnRole.INTERVIEWER;
        turn.inputMode = TurnInputMode.TEXT;
        turn.contentText = content;
        turn.followUp = true;
        turn.followUpDepth = followUpDepth;
        turn.latencyMs = latencyMs;
        turn.startedAt = Objects.requireNonNull(now);
        turn.endedAt = now;
        turn.createdAt = now;
        return turn;
    }

    private static SessionTurn baseQuestionPrompt(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            Instant now) {
        validateQuestionOwnership(session, question);
        SessionTurn turn = new SessionTurn();
        turn.session = Objects.requireNonNull(session);
        turn.question = Objects.requireNonNull(question);
        turn.turnIndex = turnIndex;
        turn.role = TurnRole.INTERVIEWER;
        turn.inputMode = TurnInputMode.TEXT;
        turn.contentText = question.getQuestionText();
        turn.followUp = false;
        turn.followUpDepth = 0;
        turn.latencyMs = 0;
        turn.startedAt = Objects.requireNonNull(now);
        turn.endedAt = now;
        turn.createdAt = now;
        return turn;
    }

    private static void validateQuestionOwnership(
            InterviewSession session,
            SessionQuestion question) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(question);
        if (!Objects.equals(question.getSession().getId(), session.getId())) {
            throw new IllegalArgumentException("Question must belong to the interview session");
        }
    }
}
