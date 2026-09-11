package com.baseProject.myBaseProject.dto.realtime;

import com.baseProject.myBaseProject.enums.RealtimeEventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RealtimeEventRequest(
        @NotBlank @Size(max = 255) String providerEventId,
        @PositiveOrZero long sequenceNumber,
        @NotNull RealtimeEventType eventType,
        @Size(max = 16000) String transcriptText,
        @Size(max = 65535) String detail,
        @PositiveOrZero @Max(600000) Integer latencyMs,
        @NotNull Instant occurredAt) {
}
