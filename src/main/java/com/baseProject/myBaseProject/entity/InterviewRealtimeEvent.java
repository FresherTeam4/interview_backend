package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.RealtimeEventType;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "interview_realtime_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_interview_realtime_event_provider",
                        columnNames = {"connection_id", "provider_event_id"}),
                @UniqueConstraint(name = "uq_interview_realtime_event_sequence",
                        columnNames = {"connection_id", "sequence_number"})
        },
        indexes = @Index(name = "idx_interview_realtime_event_pending",
                columnList = "connection_id, processed_at"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewRealtimeEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false, updatable = false)
    private InterviewVoiceConnection connection;

    @Column(name = "provider_event_id", nullable = false, updatable = false, length = 255)
    private String providerEventId;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private RealtimeEventType eventType;

    @Column(name = "transcript_text", updatable = false, columnDefinition = "TEXT")
    private String transcriptText;

    @Column(name = "detail_text", updatable = false, columnDefinition = "MEDIUMTEXT")
    private String detail;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markProcessed(Instant now) {
        processedAt = now;
    }
}
