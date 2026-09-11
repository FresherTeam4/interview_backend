package com.baseProject.myBaseProject.realtime.provider.gemini;

import com.baseProject.myBaseProject.config.properites.GeminiLiveProperties;
import com.baseProject.myBaseProject.enums.RealtimeTransport;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.realtime.ResumableRealtimeProvider;
import com.baseProject.myBaseProject.realtime.model.RealtimeSessionGrant;
import com.baseProject.myBaseProject.realtime.model.RealtimeSessionSpec;
import lombok.extern.slf4j.Slf4j;
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
import java.time.format.DateTimeParseException;

@Component
@Slf4j
public class GeminiLiveRealtimeProvider implements ResumableRealtimeProvider {
    public static final String PROVIDER_NAME = "gemini-live";
    private static final Duration SAFE_MAX_TOKEN_LIFETIME = Duration.ofHours(19);

    private final RestClient restClient;
    private final GeminiLiveProperties properties;
    private final Clock clock;
    private final GeminiLiveSessionSetupFactory setupFactory;

    public GeminiLiveRealtimeProvider(
            @Qualifier("geminiLiveRestClient") RestClient restClient,
            GeminiLiveProperties properties,
            Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
        setupFactory = new GeminiLiveSessionSetupFactory(properties.model());
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public String defaultVoice() {
        return canonicalVoice(properties.defaultVoice());
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

    // Token sống lâu hơn phiên phỏng vấn một khoảng đệm nhưng vẫn nằm dưới giới hạn của Gemini.
    private RealtimeSessionGrant requestGrant(
            RealtimeSessionSpec specification,
            String resumptionHandle) {
        requireConfigured();
        String voiceName = canonicalVoice(specification.voiceName());
        Instant now = clock.instant();
        Duration requestedLifetime = Duration.ofMinutes(specification.durationMinutes())
                .plus(properties.tokenLifetimeBuffer());
        Instant expiresAt = now.plus(min(requestedLifetime, SAFE_MAX_TOKEN_LIFETIME));
        Instant newSessionExpiresAt = now.plus(properties.newSessionTtl());
        GeminiLiveSessionSetupFactory.SessionSetups setups = setupFactory.create(
                specification.systemInstruction(), voiceName, resumptionHandle);
        // Mỗi token chỉ mở được một phiên để giảm phạm vi sử dụng nếu token bị lộ.
        GeminiLiveAuthTokenRequest body = new GeminiLiveAuthTokenRequest(
                1,
                expiresAt.toString(),
                newSessionExpiresAt.toString(),
                setups.tokenSetup());

        GeminiLiveAuthTokenResponse response;
        try {
            response = restClient.post()
                    .uri("/v1beta/auth_tokens")
                    .body(body)
                    .retrieve()
                    .body(GeminiLiveAuthTokenResponse.class);
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
                providerExpiresAt, setups.clientSetup());
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
            log.warn("Gemini Live token response omitted expireTime; using requested expiry");
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            log.warn("Gemini Live returned invalid expireTime={}; using requested expiry", value);
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
}
