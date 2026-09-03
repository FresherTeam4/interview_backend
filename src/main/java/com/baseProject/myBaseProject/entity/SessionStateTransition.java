package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.SessionTransitionActor;

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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Table(
        name = "session_state_transitions",
        indexes = {
                @Index(
                        name = "idx_session_state_transitions_session_occurred",
                        columnList = "session_id, occurred_at")
        }
)
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionStateTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private InterviewSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, updatable = false)
    private SessionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30, updatable = false)
    private SessionStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SessionTransitionActor actor;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    /** Tạo bản ghi audit bất biến cho một lần đổi trạng thái session. */
    public static SessionStateTransition create(
            InterviewSession session,
            SessionStatus fromStatus,
            SessionStatus toStatus,
            SessionTransitionActor actor,
            String reason,
            Instant occurredAt) {
        SessionStateTransition transition = new SessionStateTransition();
        transition.session = session;
        transition.fromStatus = fromStatus;
        transition.toStatus = toStatus;
        transition.actor = actor;
        transition.reason = reason;
        transition.occurredAt = occurredAt;
        return transition;
    }
}
