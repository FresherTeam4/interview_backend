package com.baseProject.myBaseProject.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * The planned script for a session, generated up front. Turns reference these rows, so a
 * follow-up can always be traced back to the question it grew out of.
 */
@Entity
@Table(
        name = "session_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_session_questions_ordinal",
                columnNames = {"session_id", "ordinal"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    /** Position in the script, starting at 1. */
    @Column(nullable = false)
    private Integer ordinal;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(length = 150)
    private String topic;

    /** 1-5. Kept loose on purpose: it is a generation hint, not a scored value. */
    private Integer difficulty;

    /** Which CV project this question was derived from, when it was derived from one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_project_id")
    private ProfileProject sourceProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_skill_id")
    private ProfileSkill sourceSkill;

    /** Seed used to generate this question; re-running on the same CV must produce different questions. */
    @Column(name = "generation_seed", length = 64)
    private String generationSeed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
