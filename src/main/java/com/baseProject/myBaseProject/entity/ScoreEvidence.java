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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "score_evidences",
        indexes = {
                @Index(name = "idx_score_evidences_score_id", columnList = "session_score_id"),
                @Index(name = "idx_score_evidences_turn_id", columnList = "turn_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_score_id", nullable = false, updatable = false)
    private SessionScore sessionScore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turn_id", nullable = false, updatable = false)
    private SessionTurn turn;

    @Column(name = "quote_text", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String quoteText;

    @Column(name = "start_offset", nullable = false, updatable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false, updatable = false)
    private int endOffset;

    public static ScoreEvidence create(
            SessionScore sessionScore,
            SessionTurn turn,
            String quoteText,
            int startOffset,
            int endOffset) {
        ScoreEvidence evidence = new ScoreEvidence();
        evidence.sessionScore = sessionScore;
        evidence.turn = turn;
        evidence.quoteText = quoteText;
        evidence.startOffset = startOffset;
        evidence.endOffset = endOffset;
        return evidence;
    }
}
