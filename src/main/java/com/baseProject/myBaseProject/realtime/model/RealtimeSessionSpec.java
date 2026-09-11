package com.baseProject.myBaseProject.realtime.model;

public record RealtimeSessionSpec(
        int durationMinutes,
        String voiceName,
        String systemInstruction) {
}
