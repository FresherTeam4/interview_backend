package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.realtime.providers.gemini-live")
public record GeminiLiveProperties(
        @NotNull URI baseUrl,
        @NotNull URI websocketEndpoint,
        String apiKey,
        @NotBlank String model,
        @NotBlank String defaultVoice,
        @NotEmpty List<@NotBlank String> supportedVoices,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration newSessionTtl,
        @NotNull Duration tokenLifetimeBuffer,
        @Positive int inputSampleRate,
        @Positive int outputSampleRate) {

    public GeminiLiveProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        supportedVoices = supportedVoices == null ? List.of() : List.copyOf(supportedVoices);
        requirePositive(connectTimeout, "Gemini Live connect timeout");
        requirePositive(readTimeout, "Gemini Live read timeout");
        requirePositive(newSessionTtl, "Gemini Live new-session TTL");
        requirePositive(tokenLifetimeBuffer, "Gemini Live token lifetime buffer");
    }

    private static void requirePositive(Duration value, String name) {
        if (value != null && (value.isZero() || value.isNegative())) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
