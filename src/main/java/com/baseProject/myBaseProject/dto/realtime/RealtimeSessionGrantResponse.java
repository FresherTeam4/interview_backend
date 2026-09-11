package com.baseProject.myBaseProject.dto.realtime;

import com.baseProject.myBaseProject.enums.RealtimeTransport;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record RealtimeSessionGrantResponse(
        Long connectionId,
        String provider,
        RealtimeTransport transport,
        URI endpoint,
        String accessToken,
        String modelName,
        String voiceName,
        RealtimeAudioFormatResponse inputAudio,
        RealtimeAudioFormatResponse outputAudio,
        Instant expiresAt,
        Map<String, Object> sessionSetup) {

    public RealtimeSessionGrantResponse {
        sessionSetup = sessionSetup == null ? Map.of() : Map.copyOf(sessionSetup);
    }
}
