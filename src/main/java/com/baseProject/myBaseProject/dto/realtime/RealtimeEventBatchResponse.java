package com.baseProject.myBaseProject.dto.realtime;

public record RealtimeEventBatchResponse(
        Long connectionId,
        int acceptedEventCount,
        int duplicateEventCount,
        int createdTurnCount,
        int currentTurnIndex) {
}
