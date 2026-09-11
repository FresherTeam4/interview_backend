package com.baseProject.myBaseProject.dto.realtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRealtimeResumeRequest(
        @NotBlank @Size(max = 65535) String resumptionHandle,
        @Size(max = 50) String clientPlatform) {
}
