package com.baseProject.myBaseProject.enums;

/**
 * Lifecycle of an interview session. Every transition is appended to
 * {@code session_state_transitions}.
 */
public enum SessionStatus {
    CREATED,
    SCRIPT_GENERATING,
    IN_PROGRESS,
    PAUSED,
    SCORING,
    COMPLETED,
    EXPIRED,
    ABANDONED
}
