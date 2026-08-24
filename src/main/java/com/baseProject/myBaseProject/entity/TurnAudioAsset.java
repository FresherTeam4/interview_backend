package com.baseProject.myBaseProject.entity;

import java.time.Instant;

import com.baseProject.myBaseProject.enums.AudioAssetKind;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pointer to an audio file in object storage. Rows may be pruned by a retention job;
 * transcripts stay behind so history remains readable.
 */
@Entity
@Table(
        name = "turn_audio_assets",
        indexes = @Index(name = "idx_turn_audio_assets_turn_kind", columnList = "turn_id, kind")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnAudioAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false)
    private SessionTurn turn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AudioAssetKind kind;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /** webm | wav | mp3 */
    @Column(nullable = false, length = 20)
    private String format;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sample_rate")
    private Integer sampleRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
