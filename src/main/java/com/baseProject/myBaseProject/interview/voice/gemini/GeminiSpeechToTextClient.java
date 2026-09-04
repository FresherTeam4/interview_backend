package com.baseProject.myBaseProject.interview.voice.gemini;

import com.baseProject.myBaseProject.ai.gemini.GeminiAdapterSupport;
import com.baseProject.myBaseProject.config.VoiceAiConfig;
import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.exception.SpeechTranscriptionException;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class GeminiSpeechToTextClient implements SpeechToTextClient {

    private static final int MAX_TRANSCRIPT_CHARS = 10_000;

    private final AiProperties credentialProperties;
    private final VoiceProperties voiceProperties;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<Client> clientProvider;
    private final String instructions;

    public GeminiSpeechToTextClient(
            AiProperties credentialProperties,
            VoiceProperties voiceProperties,
            JsonMapper jsonMapper,
            ResourceLoader resourceLoader,
            @Qualifier(VoiceAiConfig.STT_CLIENT) ObjectProvider<Client> clientProvider) {
        this.credentialProperties = credentialProperties;
        this.voiceProperties = voiceProperties;
        this.jsonMapper = jsonMapper;
        this.clientProvider = clientProvider;
        String version = voiceProperties.sttPromptVersion();
        String prompt = GeminiAdapterSupport.readClasspathResource(
                resourceLoader,
                "classpath:ai/voice-transcription-prompt-%s.txt".formatted(version));
        String schema = GeminiAdapterSupport.readClasspathResource(
                resourceLoader,
                "classpath:ai/voice-transcription-schema-%s.json".formatted(version));
        this.instructions = prompt + "\nJSON contract:\n" + schema;
    }

    @Override
    public TranscriptionOutcome transcribe(TranscriptionInput input) {
        if (!credentialProperties.hasApiKey()) {
            throw SpeechTranscriptionException.noApiKey();
        }

        Content content = Content.fromParts(
                Part.fromText(instructions
                        + "\nExpected spoken language hint: "
                        + safeLanguageCode(input.languageCode())),
                Part.fromBytes(input.audio(), input.contentType()));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .temperature(0.0f)
                .maxOutputTokens(4_096)
                .responseMimeType("application/json")
                .build();

        GenerateContentResponse response;
        try {
            response = clientProvider.getObject().models.generateContent(
                    voiceProperties.sttModel(),
                    content,
                    config);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
        return readOutcome(response);
    }

    TranscriptionOutcome readOutcome(GenerateContentResponse response) {
        if (response == null || response.text() == null || response.text().isBlank()) {
            throw SpeechTranscriptionException.malformedOutput(null);
        }
        GeneratedTranscript generated;
        try {
            generated = jsonMapper.readerFor(GeneratedTranscript.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(GeminiAdapterSupport.stripCodeFences(response.text()));
        } catch (JacksonException exception) {
            log.warn(
                    "Gemini STT output did not match prompt version {}",
                    voiceProperties.sttPromptVersion());
            throw SpeechTranscriptionException.malformedOutput(exception);
        }

        String transcript = generated.transcript() == null
                ? ""
                : generated.transcript().strip();
        if (transcript.isEmpty()
                || transcript.codePointCount(0, transcript.length()) > MAX_TRANSCRIPT_CHARS) {
            throw SpeechTranscriptionException.malformedOutput(null);
        }
        String provider = response.modelVersion()
                .filter(value -> !value.isBlank())
                .orElse(voiceProperties.sttModel());
        if (provider.length() > 50) {
            provider = provider.substring(0, 50);
        }
        return new TranscriptionOutcome(transcript, provider, null);
    }

    private SpeechTranscriptionException translate(RuntimeException exception) {
        GeminiAdapterSupport.ClassifiedFailure failure = GeminiAdapterSupport.classify(exception);
        return switch (failure.kind()) {
            case TIMEOUT -> SpeechTranscriptionException.timeout(exception);
            case RATE_LIMITED, SERVER_ERROR, API_ERROR, NETWORK_ERROR ->
                    SpeechTranscriptionException.providerUnavailable(exception);
            case CREDENTIAL_REJECTED -> SpeechTranscriptionException.noApiKey();
            case CLIENT_ERROR, UNEXPECTED -> SpeechTranscriptionException.unexpected(exception);
        };
    }

    private String safeLanguageCode(String languageCode) {
        if (languageCode == null || !languageCode.matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?")) {
            return "unknown";
        }
        return languageCode;
    }

    private record GeneratedTranscript(String transcript) {
    }
}
