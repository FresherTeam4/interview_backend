package com.baseProject.myBaseProject.dto.realtime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RealtimeEventBatchRequest(
        @NotEmpty @Size(max = 100) List<@Valid RealtimeEventRequest> events) {

    public RealtimeEventBatchRequest {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
