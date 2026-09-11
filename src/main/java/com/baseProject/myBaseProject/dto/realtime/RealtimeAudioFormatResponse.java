package com.baseProject.myBaseProject.dto.realtime;

public record RealtimeAudioFormatResponse(
        String mimeType,
        int sampleRate,
        int bitDepth,
        int channels) {
}
