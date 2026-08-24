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
 * Speech-to-text output for one turn. rawText is never overwritten so STT quality on
 * Vietnamese speech can be measured against what the user corrected.
 */
@Entity
@Table(
        name = "turn_transcripts",
        uniqueConstraints = @UniqueConstraint(name = "uq_turn_transcripts_turn", columnNames = "turn_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnTranscript {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false)
    private SessionTurn turn;

    @Column(name = "raw_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String rawText;

    /** NULL means the user left the transcript untouched. */
    @Column(name = "edited_text", columnDefinition = "TEXT")
    private String editedText;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private boolean edited = false;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "stt_provider", nullable = false, length = 50)
    private String sttProvider;

    @Column(name = "stt_confidence", precision = 4, scale = 3)
    private BigDecimal sttConfidence;

    @Column(name = "language_code", nullable = false, length = 10)
    @Builder.Default
    private String languageCode = "vi";

    /** Word-level timings from the STT provider; speech analysis depends on these. */
    @Column(name = "word_timings", columnDefinition = "JSON")
    private String wordTimings;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The text that should be scored and displayed. */
    public String effectiveText() {
        return edited && editedText != null ? editedText : rawText;
    }
}
