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

@Entity
@Table(
        name = "turn_speech_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uq_turn_speech_metrics_turn", columnNames = "turn_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnSpeechMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false)
    private SessionTurn turn;

    @Column(name = "words_per_minute", precision = 6, scale = 2)
    private BigDecimal wordsPerMinute;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "filler_count", nullable = false)
    @Builder.Default
    private int fillerCount = 0;

    @Column(name = "speaking_ms")
    private Integer speakingMs;

    @Column(name = "silence_ms", nullable = false)
    @Builder.Default
    private int silenceMs = 0;

    @Column(name = "longest_pause_ms")
    private Integer longestPauseMs;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;
}
