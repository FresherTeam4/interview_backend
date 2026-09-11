package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.enums.RealtimeTransport;

import java.util.Set;

public record RealtimeProviderCapabilities(
        Set<RealtimeTransport> transports,
        boolean nativeAudio,
        boolean voiceSelection,
        boolean inputTranscription,
        boolean outputTranscription,
        boolean interruption,
        boolean sessionResumption,
        int preferredInputSampleRate,
        int outputSampleRate) {

    public RealtimeProviderCapabilities {
        transports = transports == null ? Set.of() : Set.copyOf(transports);
        if (transports.isEmpty()) {
            throw new IllegalArgumentException("Realtime provider must support a transport");
        }
        if (preferredInputSampleRate <= 0 || outputSampleRate <= 0) {
            throw new IllegalArgumentException("Realtime audio sample rates must be positive");
        }
    }
}
