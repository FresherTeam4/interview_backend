package com.baseProject.myBaseProject.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Session-level speech numbers. Objectively measurable values only - no confidence or attitude
 * score, since inferring personality from a voice is unreliable and skews with regional accents.
 */
@Entity
@Table(
        name = "session_speech_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uq_session_speech_metrics_session", columnNames = "session_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionSpeechMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(name = "words_per_minute", precision = 6, scale = 2)
    private BigDecimal wordsPerMinute;

    @Column(name = "total_word_count")
    private Integer totalWordCount;

    @Column(name = "filler_count", nullable = false)
    @Builder.Default
    private int fillerCount = 0;

    @Column(name = "filler_per_100_words", precision = 6, scale = 2)
    private BigDecimal fillerPer100Words;

    @Column(name = "user_speaking_ms")
    private Integer userSpeakingMs;

    @Column(name = "total_silence_ms")
    private Integer totalSilenceMs;

    @Column(name = "session_duration_ms")
    private Integer sessionDurationMs;

    /** User speaking time over total session time, 0..1. */
    @Column(name = "user_talk_ratio", precision = 4, scale = 3)
    private BigDecimal userTalkRatio;

    /** Where the reference range being compared against comes from. Never blank. */
    @Column(name = "reference_source", nullable = false, length = 255)
    private String referenceSource;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;
}
