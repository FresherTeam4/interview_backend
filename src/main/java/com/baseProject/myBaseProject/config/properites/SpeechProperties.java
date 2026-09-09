package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.speech")
public record SpeechProperties(
        boolean enabled,
        @NotBlank String sttProvider,
        @NotBlank String ttsProvider,
        @Positive long maxAudioSizeBytes,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Valid @NotNull Providers providers) {

    public record Providers(@Valid @NotNull ElevenLabs elevenlabs) {
    }

    public record ElevenLabs(
            String apiKey,
            @NotNull URI baseUrl,
            @NotBlank String sttModel,
            @NotBlank String ttsModel,
            @NotBlank String outputFormat,
            Map<String, String> voices) {

        public ElevenLabs {
            apiKey = apiKey == null ? "" : apiKey.strip();
            voices = voices == null ? Map.of() : Map.copyOf(voices);
        }
    }
}
