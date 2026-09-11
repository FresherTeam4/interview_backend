package com.baseProject.myBaseProject.dto.realtime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DisconnectRealtimeConnectionRequest(
        @Size(max = 255) String reason,
        @NotNull Boolean fallbackToTurnBased,
        @PositiveOrZero @Max(600000) Integer p50LatencyMs,
        @PositiveOrZero @Max(600000) Integer p95LatencyMs) {
}
