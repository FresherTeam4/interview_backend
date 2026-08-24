package com.baseProject.myBaseProject.entity;

import java.time.Instant;

import com.baseProject.myBaseProject.enums.InterviewMode;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One exchange in the conversation, from either side. This is the spine of the session:
 * transcripts, audio, speech metrics and score evidence all hang off a turn.
 */
@Entity
@Table(
        name = "session_turns",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_session_turns_index",
                columnNames = {"session_id", "turn_index"}
        ),
        indexes = @Index(name = "idx_session_turns_parent", columnList = "parent_turn_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionTurn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    /** NULL for greeting and closing turns, which belong to no scripted question. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private SessionQuestion question;

    /** A follow-up points back at the answer it digs into. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_turn_id")
    private SessionTurn parentTurn;

    @Column(name = "turn_index", nullable = false)
    private Integer turnIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TurnRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 20)
    private InterviewMode inputMode;

    /** For a voice candidate turn this holds the final transcript that was scored. */
    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "is_followup", nullable = false)
    @Builder.Default
    private boolean followup = false;

    /** At most 2 consecutive follow-ups per topic; enforced by a check constraint. */
    @Column(name = "followup_depth", nullable = false)
    @Builder.Default
    private int followupDepth = 0;

    /** Marks an interviewer turn the user cut off (barge-in). */
    @Column(name = "was_interrupted", nullable = false)
    @Builder.Default
    private boolean interrupted = false;

    /** User stops speaking to AI audio starting. Feeds the p95 &lt; 1200ms target. */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
