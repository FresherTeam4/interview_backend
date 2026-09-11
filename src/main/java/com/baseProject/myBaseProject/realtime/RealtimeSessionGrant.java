package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.enums.RealtimeTransport;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public record RealtimeSessionGrant(
        String provider,
        RealtimeTransport transport,
        URI endpoint,
        String ephemeralToken,
        String externalSessionId,
        String modelName,
        String voiceName,
        int inputSampleRate,
        int outputSampleRate,
        Instant expiresAt,
        Map<String, Object> sessionSetup) {

    public RealtimeSessionGrant {
        sessionSetup = sessionSetup == null ? Map.of() : Map.copyOf(sessionSetup);
    }
}
