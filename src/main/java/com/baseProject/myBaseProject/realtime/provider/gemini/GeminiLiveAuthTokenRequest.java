package com.baseProject.myBaseProject.realtime.provider.gemini;

import java.util.Map;

record GeminiLiveAuthTokenRequest(
        int uses,
        String expireTime,
        String newSessionExpireTime,
        Map<String, Object> bidiGenerateContentSetup) {

    GeminiLiveAuthTokenRequest {
        bidiGenerateContentSetup = Map.copyOf(bidiGenerateContentSetup);
    }
}
