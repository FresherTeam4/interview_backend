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

    /** Tạo interviewer turn đầu tiên khi bắt đầu session. */
    public static SessionTurn firstInterviewerPrompt(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            Instant now) {
        return baseQuestionPrompt(session, question, turnIndex, now);
    }

    /** Tạo interviewer turn cho base question kế tiếp. */
    public static SessionTurn nextBaseQuestionPrompt(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            Instant now) {
        return baseQuestionPrompt(session, question, turnIndex, now);
    }

    /** Tạo candidate turn từ câu trả lời text đã được service kiểm tra. */
    public static SessionTurn candidateTextAnswer(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            String content,
            String clientTurnId,
            Instant now) {
        SessionTurn turn = new SessionTurn();
        turn.session = session;
        turn.question = question;
        turn.turnIndex = turnIndex;
        turn.role = TurnRole.CANDIDATE;
        turn.inputMode = TurnInputMode.TEXT;
        turn.contentText = content;
        turn.clientTurnId = clientTurnId;
        turn.followUp = false;
        turn.followUpDepth = 0;
        turn.startedAt = now;
        turn.endedAt = now;
        turn.createdAt = now;
        return turn;
    }

    /** Tạo follow-up turn và liên kết với candidate turn đã làm phát sinh câu hỏi. */
    public static SessionTurn followUpPrompt(
            InterviewSession session,
            SessionQuestion question,
            SessionTurn parentCandidateTurn,
            int turnIndex,
            String content,
            short followUpDepth,
            int latencyMs,
            Instant now) {
        SessionTurn turn = new SessionTurn();
        turn.session = session;
        turn.question = question;
        turn.parentTurn = parentCandidateTurn;
        turn.turnIndex = turnIndex;
        turn.role = TurnRole.INTERVIEWER;
        turn.inputMode = TurnInputMode.TEXT;
        turn.contentText = content;
        turn.followUp = true;
        turn.followUpDepth = followUpDepth;
        turn.latencyMs = latencyMs;
        turn.startedAt = now;
        turn.endedAt = now;
        turn.createdAt = now;
        return turn;
    }

    /** Khởi tạo phần dữ liệu chung của một interviewer base-question turn. */
    private static SessionTurn baseQuestionPrompt(
            InterviewSession session,
            SessionQuestion question,
            int turnIndex,
            Instant now) {
        SessionTurn turn = new SessionTurn();
        turn.session = session;
        turn.question = question;
        turn.turnIndex = turnIndex;
        turn.role = TurnRole.INTERVIEWER;
        turn.inputMode = TurnInputMode.TEXT;
        turn.contentText = question.getQuestionText();
        turn.followUp = false;
        turn.followUpDepth = 0;
        turn.latencyMs = 0;
        turn.startedAt = now;
        turn.endedAt = now;
        turn.createdAt = now;
        return turn;
    }

}
