package com.baseProject.myBaseProject.realtime.provider.gemini;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GeminiLiveSessionSetupFactory {
    private final String modelResourceName;

    GeminiLiveSessionSetupFactory(String model) {
        modelResourceName = model.startsWith("models/") ? model : "models/" + model;
    }

    SessionSetups create(
            String systemInstruction,
            String voiceName,
            String resumptionHandle) {
        Map<String, Object> generationConfig = Map.of(
                "responseModalities", List.of("AUDIO"),
                "speechConfig", Map.of(
                        "voiceConfig", Map.of(
                                "prebuiltVoiceConfig", Map.of("voiceName", voiceName))));
        Map<String, Object> resumptionConfig = resumptionHandle == null
                ? Map.of()
                : Map.of("handle", resumptionHandle);

        Map<String, Object> clientSetup = new LinkedHashMap<>();
        clientSetup.put("model", modelResourceName);
        clientSetup.put("generationConfig", generationConfig);
        clientSetup.put("inputAudioTranscription", Map.of());
        clientSetup.put("outputAudioTranscription", Map.of());
        clientSetup.put("contextWindowCompression", Map.of("slidingWindow", Map.of()));
        clientSetup.put("sessionResumption", resumptionConfig);

        // System instruction chỉ nằm trong token để client không thể thay đổi interview plan.
        Map<String, Object> tokenSetup = new LinkedHashMap<>(clientSetup);
        tokenSetup.put("systemInstruction", content(systemInstruction));
        return new SessionSetups(tokenSetup, clientSetup);
    }

    private Map<String, Object> content(String text) {
        return Map.of("parts", List.of(Map.of("text", text)));
    }

    record SessionSetups(
            Map<String, Object> tokenSetup,
            Map<String, Object> clientSetup) {

        SessionSetups {
            tokenSetup = Map.copyOf(tokenSetup);
            clientSetup = Map.copyOf(clientSetup);
        }
    }
}
