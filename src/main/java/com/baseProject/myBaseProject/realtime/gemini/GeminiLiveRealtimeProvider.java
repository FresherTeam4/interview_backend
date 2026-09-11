package com.baseProject.myBaseProject.realtime.gemini;

import com.baseProject.myBaseProject.config.properites.GeminiLiveProperties;
import com.baseProject.myBaseProject.enums.RealtimeTransport;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.realtime.RealtimeInterviewProvider;
import com.baseProject.myBaseProject.realtime.RealtimeProviderCapabilities;
import com.baseProject.myBaseProject.realtime.RealtimeSessionGrant;
import com.baseProject.myBaseProject.realtime.RealtimeSessionSpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GeminiLiveRealtimeProvider implements RealtimeInterviewProvider {
    public static final String PROVIDER_NAME = "gemini-live";
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofHours(19);

    private final RestClient restClient;
    private final GeminiLiveProperties properties;
    private final Clock clock;

    public GeminiLiveRealtimeProvider(
            @Qualifier("geminiLiveRestClient") RestClient restClient,
            GeminiLiveProperties properties,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public RealtimeProviderCapabilities capabilities() {
        return new RealtimeProviderCapabilities(
                Set.of(RealtimeTransport.WEBSOCKET),
                true, true, true, true, true, true,
                properties.inputSampleRate(), properties.outputSampleRate());
    }

    @Override
    public String defaultVoice() {
        return canonicalVoice(properties.defaultVoice());
    }

    @Override
    public Set<String> supportedVoices() {
        return Set.copyOf(properties.supportedVoices());
    }

    @Override
    public RealtimeSessionGrant createSession(RealtimeSessionSpec specification) {
        return requestGrant(specification, null);
    }

    @Override
    public RealtimeSessionGrant resumeSession(
            RealtimeSessionSpec specification,
            String resumptionHandle) {
        if (resumptionHandle == null || resumptionHandle.isBlank()) {
            throw new DomainException(
                    ErrorCode.REALTIME_CONFIG_ERROR,
                    "Gemini Live resumption handle is required");
        }
        return requestGrant(specification, resumptionHandle.strip());
    }

    private RealtimeSessionGrant requestGrant(
            RealtimeSessionSpec specification,
            String resumptionHandle) {
        requireConfigured();
        String voiceName = canonicalVoice(specification.voiceName());
        Instant now = clock.instant();
        Duration requestedLifetime = Duration.ofMinutes(specification.durationMinutes())
                .plus(properties.tokenLifetimeBuffer());
        Instant expiresAt = now.plus(min(requestedLifetime, MAX_TOKEN_LIFETIME));
        Instant newSessionExpiresAt = now.plus(properties.newSessionTtl());
        Map<String, Object> liveConfig = liveConfig(
                specification.systemInstruction(), voiceName, resumptionHandle);
        Map<String, Object> body = Map.of(
                "uses", 1,
                "expireTime", expiresAt.toString(),
                "newSessionExpireTime", newSessionExpiresAt.toString(),
                "bidiGenerateContentSetup", providerSetup(liveConfig));

        AuthTokenResponse response;
        try {
            response = restClient.post()
                    .uri("/v1beta/auth_tokens")
                    .body(body)
                    .retrieve()
                    .body(AuthTokenResponse.class);
        } catch (RestClientResponseException exception) {
            throw translateResponseError(exception);
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                throw new DomainException(ErrorCode.REALTIME_PROVIDER_TIMEOUT, exception);
            }
            throw new DomainException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception);
        }
        if (response == null || response.name() == null || response.name().isBlank()) {
            throw new DomainException(
                    ErrorCode.REALTIME_PROVIDER_ERROR,
                    "Gemini Live returned an empty ephemeral token");
        }

        Instant providerExpiresAt = parseInstant(response.expireTime(), expiresAt);
        return new RealtimeSessionGrant(
                name(), RealtimeTransport.WEBSOCKET,
                properties.websocketEndpoint(), response.name(), null,
                properties.model(), voiceName,
                properties.inputSampleRate(), properties.outputSampleRate(),
                providerExpiresAt, sessionSetup(liveConfig));
    }

    private Map<String, Object> liveConfig(
            String systemInstruction,
            String voiceName,
            String resumptionHandle) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("responseModalities", List.of("AUDIO"));
        config.put("speechConfig", Map.of(
                "voiceConfig", Map.of(
                        "prebuiltVoiceConfig", Map.of("voiceName", voiceName))));
        config.put("systemInstruction", content(systemInstruction));
        config.put("inputAudioTranscription", Map.of());
        config.put("outputAudioTranscription", Map.of());
        config.put("contextWindowCompression", Map.of("slidingWindow", Map.of()));
        config.put("sessionResumption", resumptionHandle == null
                ? Map.of()
                : Map.of("handle", resumptionHandle));
        return Map.copyOf(config);
    }

    private Map<String, Object> sessionSetup(Map<String, Object> liveConfig) {
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("model", modelResourceName());
        setup.put("generationConfig", Map.of(
                "responseModalities", liveConfig.get("responseModalities"),
                "speechConfig", liveConfig.get("speechConfig")));
        setup.put("inputAudioTranscription", liveConfig.get("inputAudioTranscription"));
        setup.put("outputAudioTranscription", liveConfig.get("outputAudioTranscription"));
        setup.put("contextWindowCompression", liveConfig.get("contextWindowCompression"));
        setup.put("sessionResumption", liveConfig.get("sessionResumption"));
        return Map.copyOf(setup);
    }

    private Map<String, Object> providerSetup(Map<String, Object> liveConfig) {
        Map<String, Object> setup = new LinkedHashMap<>(sessionSetup(liveConfig));
        setup.put("systemInstruction", liveConfig.get("systemInstruction"));
        return Map.copyOf(setup);
    }

    private Map<String, Object> content(String text) {
        return Map.of("parts", List.of(Map.of("text", text)));
    }

    private String canonicalVoice(String requested) {
        String candidate = requested == null || requested.isBlank()
                ? properties.defaultVoice()
                : requested.strip();
        return properties.supportedVoices().stream()
                .filter(voice -> voice.equalsIgnoreCase(candidate))
                .findFirst()
                .orElseThrow(() -> new DomainException(
                        ErrorCode.REALTIME_VOICE_NOT_SUPPORTED,
                        "Unsupported Gemini Live voice: " + candidate));
    }

    private void requireConfigured() {
        if (properties.apiKey().isBlank()) {
            throw new DomainException(
                    ErrorCode.REALTIME_CONFIG_ERROR,
                    "GEMINI_API_KEY is required when realtime interview is enabled");
        }
        canonicalVoice(properties.defaultVoice());
    }

    private String modelResourceName() {
        return properties.model().startsWith("models/")
                ? properties.model()
                : "models/" + properties.model();
    }

    private DomainException translateResponseError(RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        if (status.value() == 401 || status.value() == 403) {
            return new DomainException(ErrorCode.REALTIME_CONFIG_ERROR, exception);
        }
        if (status.value() == 408 || status.value() == 504) {
            return new DomainException(ErrorCode.REALTIME_PROVIDER_TIMEOUT, exception);
        }
        if (status.value() == 429 || status.is5xxServerError()) {
            return new DomainException(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE, exception);
        }
        return new DomainException(ErrorCode.REALTIME_PROVIDER_ERROR, exception);
    }

    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record AuthTokenResponse(
            String name,
            String expireTime,
            String newSessionExpireTime) {
    }
}
