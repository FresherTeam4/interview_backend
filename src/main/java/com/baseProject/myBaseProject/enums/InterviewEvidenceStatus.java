package com.baseProject.myBaseProject.enums;

public enum InterviewEvidenceStatus {
    NOT_EXPLORED(0),
    PARTIAL(1),
    SUFFICIENT(2);

    private final int level;

    InterviewEvidenceStatus(int level) {
        this.level = level;
    }

    public boolean isAtLeast(InterviewEvidenceStatus other) {
        return level >= other.level;
    }
}
