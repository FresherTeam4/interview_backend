package com.baseProject.myBaseProject.enums;

public enum SessionStatus {
    CREATED,
    SCRIPT_GENERATING,
    READY,
    IN_PROGRESS,
    PAUSED,
    SCORING,
    COMPLETED,
    ABANDONED,
    FAILED;

    public boolean isActive() {
        return this != COMPLETED && this != ABANDONED;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == ABANDONED;
    }
}
