package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "voice_answer_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_voice_attempts_session_client",
                        columnNames = {"session_id", "client_attempt_id"}),
                @UniqueConstraint(
                        name = "uq_voice_attempts_session_question_no",
                        columnNames = {"session_id", "question_id", "attempt_no"}),
                @UniqueConstraint(
                        name = "uq_voice_attempts_confirmed_turn",
                        columnNames = "confirmed_turn_id")
        },
        indexes = {
                @Index(name = "idx_voice_attempts_question_id", columnList = "question_id"),
                @Index(name = "idx_voice_attempts_prompt_turn_id", columnList = "prompt_turn_id"),
                @Index(
                        name = "idx_voice_attempts_recovery",
                        columnList = "status, processing_started_at"),
                @Index(
                        name = "idx_voice_attempts_audio_deleted",
                        columnList = "audio_deleted_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoiceAnswerAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private SessionQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prompt_turn_id", nullable = false, updatable = false)
    private SessionTurn promptTurn;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_turn_id")
    private SessionTurn confirmedTurn;

    @Column(name = "client_attempt_id", nullable = false, length = 64, updatable = false)
    private String clientAttemptId;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private short attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VoiceAttemptStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "storage_key", nullable = false, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private AudioFormat format;

    @Column(name = "file_size_bytes", nullable = false, updatable = false)
    private long fileSizeBytes;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    @Column(
            name = "checksum_sha256",
            nullable = false,
            length = 64,
            updatable = false,
            columnDefinition = "CHAR(64)")
    private String checksumSha256;

    @Column(name = "raw_text", columnDefinition = "MEDIUMTEXT")
    private String rawText;

    @Column(name = "edited_text", columnDefinition = "MEDIUMTEXT")
    private String editedText;

    @Column(name = "stt_provider", length = 50)
    private String sttProvider;

    @Column(name = "stt_confidence", precision = 4, scale = 3)
    private BigDecimal sttConfidence;

    @Column(name = "processing_token", length = 36, columnDefinition = "CHAR(36)")
    private String processingToken;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_attempts", nullable = false)
    private short processingAttempts;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "audio_deleted_at")
    private Instant audioDeletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "transcribed_at")
    private Instant transcribedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public static VoiceAnswerAttempt recorded(
            InterviewSession session,
            SessionTurn promptTurn,
            short attemptNo,
            String clientAttemptId,
            String storageKey,
            AudioFormat format,
            long fileSizeBytes,
            int durationMs,
            String checksumSha256,
            Instant createdAt) {
        VoiceAnswerAttempt attempt = new VoiceAnswerAttempt();
        attempt.session = session;
        attempt.question = promptTurn.getQuestion();
        attempt.promptTurn = promptTurn;
        attempt.clientAttemptId = clientAttemptId;
        attempt.attemptNo = attemptNo;
        attempt.status = VoiceAttemptStatus.RECORDED;
        attempt.storageKey = storageKey;
        attempt.contentType = format.contentType();
        attempt.format = format;
        attempt.fileSizeBytes = fileSizeBytes;
        attempt.durationMs = durationMs;
        attempt.checksumSha256 = checksumSha256;
        attempt.createdAt = createdAt;
        return attempt;
    }

    public void completeTranscription(
            String transcript,
            String provider,
            BigDecimal confidence,
            Instant now) {
        status = VoiceAttemptStatus.TRANSCRIBED;
        rawText = transcript;
        editedText = null;
        sttProvider = provider;
        sttConfidence = confidence;
        processingToken = null;
        processingStartedAt = null;
        nextRetryAt = null;
        statusMessage = null;
        transcribedAt = now;
    }

    public void releaseTranscriptionForRetry(Instant retryAt, String message) {
        status = VoiceAttemptStatus.RECORDED;
        processingToken = null;
        processingStartedAt = null;
        nextRetryAt = retryAt;
        statusMessage = message;
    }

    public void failTranscription(String message) {
        status = VoiceAttemptStatus.FAILED;
        processingToken = null;
        processingStartedAt = null;
        nextRetryAt = null;
        statusMessage = message;
    }

    public void editTranscript(String normalizedText) {
        editedText = rawText.equals(normalizedText) ? null : normalizedText;
        statusMessage = null;
    }

    public void confirm(SessionTurn candidateTurn, Instant now) {
        confirmedTurn = candidateTurn;
        status = VoiceAttemptStatus.CONFIRMED;
        processingToken = null;
        processingStartedAt = null;
        nextRetryAt = null;
        statusMessage = null;
        confirmedAt = now;
    }

    public void discard() {
        if (status == VoiceAttemptStatus.CONFIRMED) {
            throw new IllegalStateException("A confirmed voice attempt cannot be discarded");
        }
        status = VoiceAttemptStatus.DISCARDED;
        processingToken = null;
        processingStartedAt = null;
        nextRetryAt = null;
        statusMessage = null;
    }
}
