package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.RealtimeTransport;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "interview_voice_connections",
        indexes = {
                @Index(name = "idx_interview_voice_connection_session",
                        columnList = "session_id, created_at"),
                @Index(name = "idx_interview_voice_connection_active",
                        columnList = "session_id, disconnected_at")
        })
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewVoiceConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Column(nullable = false, updatable = false, length = 50)
    private String provider;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 20)
    private RealtimeTransport transport;

    @Column(name = "model_name", nullable = false, updatable = false, length = 100)
    private String modelName;

    @Column(name = "voice_name", nullable = false, updatable = false, length = 50)
    private String voiceName;

    @Column(name = "external_session_id", length = 255)
    private String externalSessionId;

    @Column(name = "resumption_handle", columnDefinition = "MEDIUMTEXT")
    private String resumptionHandle;

    @Column(name = "client_platform", updatable = false, length = 50)
    private String clientPlatform;

    @Column(name = "input_sample_rate", updatable = false)
    private Integer inputSampleRate;

    @Column(name = "output_sample_rate", updatable = false)
    private Integer outputSampleRate;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    @Column(name = "disconnect_reason", length = 255)
    private String disconnectReason;

    @Column(name = "fell_back_to_turn_based", nullable = false)
    @Builder.Default
    private boolean fellBackToTurnBased = false;

    @Column(name = "p50_latency_ms")
    private Integer p50LatencyMs;

    @Column(name = "p95_latency_ms")
    private Integer p95LatencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markConnected(String externalSessionId, Instant now) {
        this.externalSessionId = externalSessionId;
        connectedAt = now;
    }

    public void updateResumptionHandle(String resumptionHandle) {
        this.resumptionHandle = resumptionHandle;
    }

    public void markDisconnected(
            String reason,
            boolean fellBackToTurnBased,
            Integer p50LatencyMs,
            Integer p95LatencyMs,
            Instant now) {
        disconnectReason = reason;
        this.fellBackToTurnBased = fellBackToTurnBased;
        this.p50LatencyMs = p50LatencyMs;
        this.p95LatencyMs = p95LatencyMs;
        disconnectedAt = now;
    }
}
