package com.baseProject.myBaseProject.dto.realtime;

import com.baseProject.myBaseProject.enums.InterviewSessionMode;

import java.time.Instant;

public record RealtimeConnectionResponse(
        Long connectionId,
        InterviewSessionMode sessionMode,
        Instant connectedAt,
        Instant disconnectedAt,
        String disconnectReason,
        boolean fellBackToTurnBased,
        Integer p50LatencyMs,
        Integer p95LatencyMs) {
}
