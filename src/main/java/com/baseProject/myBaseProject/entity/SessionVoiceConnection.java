package com.baseProject.myBaseProject.entity;

import java.time.Instant;

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
 * One realtime voice link. Latency is recorded per connection because the p95 &lt; 1200ms
 * target has to be measured, not estimated.
 */
@Entity
@Table(
        name = "session_voice_connections",
        indexes = @Index(name = "idx_session_voice_connections_session", columnList = "session_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionVoiceConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    /** chrome-desktop | safari-ios ... */
    @Column(name = "client_platform", length = 50)
    private String clientPlatform;

    /** On a dropped realtime link the session falls back to turn-based and keeps its context. */
    @Column(name = "fell_back_to_turn_based", nullable = false)
    @Builder.Default
    private boolean fellBackToTurnBased = false;

    @Column(name = "p50_latency_ms")
    private Integer p50LatencyMs;

    @Column(name = "p95_latency_ms")
    private Integer p95LatencyMs;

    @Column(name = "disconnect_reason", length = 255)
    private String disconnectReason;
}
