package com.baseProject.myBaseProject.interview.lifecycle.model;

public enum SessionEvent {
    DISPATCH_SCRIPT_GENERATION,
    SCRIPT_PERSISTED,
    START,
    PAUSE,
    RESUME,
    ALL_QUESTIONS_ANSWERED,
    COMPLETE_EARLY,
    TIMEOUT,
    REPORT_COMMITTED,
    WORKFLOW_FAILED,
    ABANDON,
    RETRY
}
