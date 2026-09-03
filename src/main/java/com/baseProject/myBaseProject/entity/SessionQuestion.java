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
                        name = "idx_session_questions_project_snapshot_id",
                        columnList = "source_project_snapshot_id"),
                @Index(
                        name = "idx_session_questions_skill_snapshot_id",
                        columnList = "source_skill_snapshot_id")
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

    @Column(name = "source_project_snapshot_id", updatable = false)
    private Long sourceProjectSnapshotId;

    @Column(name = "source_skill_snapshot_id", updatable = false)
    private Long sourceSkillSnapshotId;

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

    /** Khởi tạo một base question bất biến từ dữ liệu đã được AI validator chấp nhận. */
    public static SessionQuestion create(
            InterviewSession session,
            CreationData data,
            Instant createdAt) {
        SessionQuestion question = new SessionQuestion();
        question.session = session;
        question.ordinal = data.ordinal();
        question.questionText = data.questionText();
        question.topic = data.topic();
        question.competency = data.competency();
        question.difficulty = data.difficulty();
        question.sourceType = data.sourceType();
        question.sourceProjectSnapshotId = data.sourceProjectSnapshotId();
        question.sourceSkillSnapshotId = data.sourceSkillSnapshotId();
        question.sourceJdExcerpt = data.sourceJdExcerpt();
        question.questionSignature = data.questionSignature();
        question.generationSeed = data.generationSeed().toString();
        question.promptVersion = data.promptVersion();
        question.modelName = data.modelName();
        question.createdAt = createdAt;
        return question;
    }

    /** Gom dữ liệu đầu vào để tránh factory của entity có quá nhiều tham số rời rạc. */
    public record CreationData(
            short ordinal,
            String questionText,
            String topic,
            String competency,
            short difficulty,
            QuestionSourceType sourceType,
            Long sourceProjectSnapshotId,
            Long sourceSkillSnapshotId,
            String sourceJdExcerpt,
            String questionSignature,
            UUID generationSeed,
            String promptVersion,
            String modelName) {
    }
}
