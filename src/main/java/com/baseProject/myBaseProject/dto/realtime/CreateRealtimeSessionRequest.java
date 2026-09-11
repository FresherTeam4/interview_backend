package com.baseProject.myBaseProject.dto.realtime;

import jakarta.validation.constraints.Size;

public record CreateRealtimeSessionRequest(
        @Size(max = 50) String voiceName,
        @Size(max = 50) String clientPlatform) {
}
