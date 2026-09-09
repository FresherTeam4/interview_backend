package com.baseProject.myBaseProject.speech.provider.elevenlabs;

import com.baseProject.myBaseProject.config.properites.SpeechProperties;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.speech.SpeechToTextProvider;
import com.baseProject.myBaseProject.speech.TextToSpeechProvider;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisRequest;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionRequest;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
public class ElevenLabsSpeechProvider
        implements SpeechToTextProvider, TextToSpeechProvider {
    public static final String PROVIDER_NAME = "elevenlabs";
    private static final String OUTPUT_CONTENT_TYPE = "audio/mpeg";

    private final RestClient restClient;
    private final SpeechProperties.ElevenLabs properties;
    private final ObjectMapper objectMapper;

    public ElevenLabsSpeechProvider(
            @Qualifier("elevenLabsRestClient") RestClient restClient,
            SpeechProperties speechProperties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = speechProperties.providers().elevenlabs();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public SpeechTranscriptionResult transcribe(SpeechTranscriptionRequest request) {
        requireApiKey();
        try {
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            ByteArrayResource resource = new ByteArrayResource(request.audio()) {
                @Override
                public String getFilename() {
                    return filename(request.filename());
                }
            };
            body.part("file", resource)
                    .contentType(mediaType(request.contentType()));
            body.part("model_id", properties.sttModel());
            body.part("language_code", normalizeLanguage(request.languageCode()));
            // MVP chỉ cần nội dung câu trả lời, không cần nhãn âm thanh hoặc phân tách người nói.
            body.part("tag_audio_events", "false");
            body.part("diarize", "false");

            ElevenLabsTranscriptionResponse response = restClient.post()
                    .uri("/v1/speech-to-text")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(ElevenLabsTranscriptionResponse.class);
            if (response == null) {
                throw new DomainException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
            }
            return new SpeechTranscriptionResult(
                    response.text(), response.languageCode());
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (ResourceAccessException exception) {
            throw translate(exception);
        }
    }

    @Override
    public String cacheIdentity(String languageCode) {
        requireMp3Output();
        String language = normalizeLanguage(languageCode);
        return String.join(":",
                PROVIDER_NAME,
                properties.ttsModel(),
                properties.outputFormat(),
                language,
                voiceId(language));
    }

    @Override
    public String outputContentType() {
        return OUTPUT_CONTENT_TYPE;
    }

    @Override
    public SpeechSynthesisResult synthesize(SpeechSynthesisRequest request) {
        requireApiKey();
        requireMp3Output();
        String voiceId = voiceId(request.languageCode());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", request.text());
        body.put("model_id", properties.ttsModel());
        body.put("language_code", normalizeLanguage(request.languageCode()));

        try {
            byte[] audio = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/text-to-speech/{voiceId}")
                            .queryParam("output_format", properties.outputFormat())
                            .build(voiceId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf(OUTPUT_CONTENT_TYPE))
                    .body(body)
                    .retrieve()
                    .body(byte[].class);

            return new SpeechSynthesisResult(audio, OUTPUT_CONTENT_TYPE);
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (ResourceAccessException exception) {
            throw translate(exception);
        }
    }

    private void requireApiKey() {
        if (properties.apiKey().isBlank()) {
            throw new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "ElevenLabs API key is not configured");
        }
    }

    private void requireMp3Output() {
        // API chung luôn trả audio/mpeg nên adapter ElevenLabs phải giữ đầu ra ở định dạng MP3.
        if (!properties.outputFormat().startsWith("mp3_")) {
            throw new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "ElevenLabs output format must be MP3");
        }
    }

    private String voiceId(String languageCode) {
        String language = normalizeLanguage(languageCode);
        String voiceId = properties.voices().get(language);
        if (voiceId == null || voiceId.isBlank()) {
            throw new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "ElevenLabs voice is not configured for language: " + language);
        }

        return voiceId.strip();
    }

    private DomainException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        logProviderError(exception, status);

        if (status == 401 || status == 403) {
            return new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "ElevenLabs rejected the configured credentials",
                    exception);
        }
        if (status == 408 || status == 504) {
            return new DomainException(ErrorCode.SPEECH_PROVIDER_TIMEOUT, exception);
        }
        if (status == 429 || status >= 500) {
            return new DomainException(ErrorCode.SPEECH_PROVIDER_UNAVAILABLE, exception);
        }
        return new DomainException(
                ErrorCode.SPEECH_PROVIDER_ERROR,
                "ElevenLabs rejected the speech request",
                exception);
    }

    private void logProviderError(
            RestClientResponseException exception, int status) {
        ElevenLabsError error = readProviderError(exception);
        log.warn(
                "ElevenLabs request failed: httpStatus={}, errorType={}, errorCode={}, requestId={}, message={}",
                status,
                safeLogValue(error.type()),
                safeLogValue(error.code()),
                safeLogValue(requestId(exception.getResponseHeaders())),
                safeLogValue(error.message()));
    }

    private ElevenLabsError readProviderError(
        RestClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode detail = body == null ? null : body.get("detail");
            if (detail == null || detail.isNull()) {
                return ElevenLabsError.EMPTY;
            }
            if (detail.isTextual()) {
                return new ElevenLabsError(null, null, detail.asText());
            }

            String code = textValue(detail, "code");
            if (code == null) {
                code = textValue(detail, "status");
            }
            return new ElevenLabsError(
                    textValue(detail, "type"),
                    code,
                    textValue(detail, "message"));
        } catch (RuntimeException parseException) {
            log.debug("Could not parse ElevenLabs error response, status={}",
                    exception.getStatusCode().value());
            return ElevenLabsError.EMPTY;
        }
    }

    private String requestId(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String requestId = headers.getFirst("request-id");
        return requestId != null ? requestId : headers.getFirst("x-trace-id");
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String safeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("\\p{Cntrl}", " ").strip();
        return sanitized.length() <= 500
                ? sanitized
                : sanitized.substring(0, 500) + "...";
    }

    private DomainException translate(ResourceAccessException exception) {
        ErrorCode code = hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, HttpTimeoutException.class)
                ? ErrorCode.SPEECH_PROVIDER_TIMEOUT
                : ErrorCode.SPEECH_PROVIDER_UNAVAILABLE;

        return new DomainException(code, exception);
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizeLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            throw new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "Interview language is missing");
        }
        return languageCode.strip().split("-", 2)[0].toLowerCase(Locale.ROOT);
    }

    private String filename(String filename) {
        return filename == null || filename.isBlank() ? "answer.webm" : filename;
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ElevenLabsTranscriptionResponse(
            String text,
            @JsonProperty("language_code") String languageCode) {
    }

    private record ElevenLabsError(String type, String code, String message) {
        private static final ElevenLabsError EMPTY =
                new ElevenLabsError(null, null, null);
    }
}
