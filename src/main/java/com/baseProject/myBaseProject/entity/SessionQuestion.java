package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.QuestionSourceType;

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
import java.util.UUID;

@Entity
@Table(
        name = "session_questions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_session_questions_session_ordinal",
                        columnNames = {"session_id", "ordinal"}),
                @UniqueConstraint(
                        name = "uq_session_questions_session_signature",
                        columnNames = {"session_id", "question_signature"})
        },
        indexes = {
                @Index(
                        name = "idx_session_questions_project_id",
                        columnList = "source_project_id"),
                @Index(
                        name = "idx_session_questions_skill_id",
                        columnList = "source_skill_id")
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(nullable = false, updatable = false)
    private short ordinal;

    @Column(name = "question_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, length = 150, updatable = false)
    private String topic;

    @Column(nullable = false, length = 100, updatable = false)
    private String competency;

    @Column(nullable = false, updatable = false)
    private short difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30, updatable = false)
    private QuestionSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_project_id", updatable = false)
    private ProfileProject sourceProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_skill_id", updatable = false)
    private ProfileSkill sourceSkill;

    @Column(name = "source_jd_excerpt", updatable = false, columnDefinition = "TEXT")
    private String sourceJdExcerpt;

    @Column(
            name = "question_signature",
            nullable = false,
            length = 64,
            updatable = false,
            columnDefinition = "CHAR(64)")
    private String questionSignature;

    @Column(
            name = "generation_seed",
            nullable = false,
            length = 36,
            updatable = false,
            columnDefinition = "CHAR(36)")
    private String generationSeed;

    @Column(name = "prompt_version", nullable = false, length = 20, updatable = false)
    private String promptVersion;

    @Column(name = "model_name", nullable = false, length = 100, updatable = false)
    private String modelName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SessionQuestion create(
            InterviewSession session,
            short ordinal,
            String questionText,
            String topic,
            String competency,
            short difficulty,
            QuestionSourceType sourceType,
            ProfileProject sourceProject,
            ProfileSkill sourceSkill,
            String sourceJdExcerpt,
            String questionSignature,
            UUID generationSeed,
            String promptVersion,
            String modelName,
            Instant createdAt) {
        SessionQuestion question = new SessionQuestion();
        question.session = Objects.requireNonNull(session);
        question.ordinal = ordinal;
        question.questionText = Objects.requireNonNull(questionText);
        question.topic = Objects.requireNonNull(topic);
        question.competency = Objects.requireNonNull(competency);
        question.difficulty = difficulty;
        question.sourceType = Objects.requireNonNull(sourceType);
        question.sourceProject = sourceProject;
        question.sourceSkill = sourceSkill;
        question.sourceJdExcerpt = sourceJdExcerpt;
        question.questionSignature = Objects.requireNonNull(questionSignature);
        question.generationSeed = Objects.requireNonNull(generationSeed).toString();
        question.promptVersion = Objects.requireNonNull(promptVersion);
        question.modelName = Objects.requireNonNull(modelName);
        question.createdAt = Objects.requireNonNull(createdAt);
        return question;
    }
}
