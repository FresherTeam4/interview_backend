package com.baseProject.myBaseProject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Where a filler word actually occurred, so the transcript can highlight it instead of only
 * showing a count.
 */
@Entity
@Table(
        name = "turn_filler_occurrences",
        indexes = @Index(name = "idx_turn_filler_occurrences_turn", columnList = "turn_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnFillerOccurrence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false)
    private SessionTurn turn;

    @Column(nullable = false, length = 50)
    private String word;

    /** Position in the recording, in milliseconds. */
    @Column(name = "occurred_at_ms", nullable = false)
    private Integer occurredAtMs;
}
