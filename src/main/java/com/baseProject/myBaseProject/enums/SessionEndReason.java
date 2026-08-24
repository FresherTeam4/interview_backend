package com.baseProject.myBaseProject.enums;

/**
 * Why a session stopped. Only set once the session leaves an active state.
 */
public enum SessionEndReason {
    USER_COMPLETED,
    USER_ABANDONED,
    TIMEOUT_24H,
    SYSTEM_ERROR
}
