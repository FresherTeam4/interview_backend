package com.baseProject.myBaseProject.interview.ai.gemini;

import com.baseProject.myBaseProject.ai.gemini.GeminiAdapterSupport;
import com.baseProject.myBaseProject.config.InterviewAiConfig;
import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.exception.InterviewScoringException;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.GeneratedScoringResult;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringInput;
import com.baseProject.myBaseProject.interview.ai.model.ScoringContract.ScoringOutcome;
import com.baseProject.myBaseProject.interview.ai.port.InterviewScorer;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class GeminiInterviewScorer implements InterviewScorer {

    private final AiProperties credentialProperties;
    private final InterviewAiProperties properties;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<GoogleGenAiChatModel> chatModelProvider;
    private final String promptTemplate;
    private final String responseSchema;

    public GeminiInterviewScorer(
            AiProperties credentialProperties,
            InterviewAiProperties properties,
            JsonMapper jsonMapper,
            ResourceLoader resourceLoader,
            @Qualifier(InterviewAiConfig.SCORING_CHAT_MODEL)
            ObjectProvider<GoogleGenAiChatModel> chatModelProvider) {
        this.credentialProperties = credentialProperties;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.chatModelProvider = chatModelProvider;

        String version = properties.scoringPromptVersion();
        this.promptTemplate = GeminiAdapterSupport.readClasspathResource(
                resourceLoader,
                "classpath:ai/interview-score-prompt-%s.txt".formatted(version));
        String schemaContract = GeminiAdapterSupport.readClasspathResource(
                resourceLoader,
                "classpath:ai/interview-score-schema-%s.json".formatted(version));
        this.responseSchema = GeminiAdapterSupport.toSpringAiSchema(
                jsonMapper,
                schemaContract);
    }

    @Override
    public ScoringOutcome score(ScoringInput input) {
        if (!credentialProperties.hasApiKey()) {
            throw InterviewScoringException.noApiKey();
        }

        Prompt request = buildRequest(input);
        long startedAtNanos = System.nanoTime();
        ChatResponse response = call(request);
        int durationMs = Math.toIntExact(
                Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis());
        return readOutcome(response, durationMs);
    }

    private Prompt buildRequest(ScoringInput input) {
        SystemMessage instructions = new SystemMessage(promptTemplate);
        UserMessage context = UserMessage.builder()
                .text("<UNTRUSTED_SCORING_CONTEXT>\n"
                        + jsonMapper.writeValueAsString(input)
                        + "\n</UNTRUSTED_SCORING_CONTEXT>")
                .build();
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(properties.model())
                .outputSchema(responseSchema)
                .build();
        return new Prompt(List.of(instructions, context), options);
    }

    private ChatResponse call(Prompt request) {
        try {
            return chatModelProvider.getObject().call(request);
        } catch (RuntimeException exception) {
            GeminiAdapterSupport.ClassifiedFailure failure =
                    GeminiAdapterSupport.classify(exception);
            switch (failure.kind()) {
                case TIMEOUT -> throw InterviewScoringException.timeout(exception);
                case RATE_LIMITED -> {
                    log.warn("Gemini rate-limited interview scoring");
                    throw InterviewScoringException.providerUnavailable("HTTP 429", exception);
                }
                case CREDENTIAL_REJECTED -> {
                    log.error("Gemini credential rejected for interview scoring");
                    throw InterviewScoringException.noApiKey();
                }
                case CLIENT_ERROR -> {
                    log.error(
                            "Gemini rejected interview scoring request with HTTP {}",
                            failure.statusCode());
                    throw InterviewScoringException.unexpected(exception);
                }
                case SERVER_ERROR, API_ERROR -> {
                    log.warn(
                            "Gemini returned HTTP {} during interview scoring",
                            failure.statusCode());
                    throw InterviewScoringException.providerUnavailable(
                            "HTTP " + failure.statusCode(),
                            exception);
                }
                case NETWORK_ERROR -> throw InterviewScoringException.providerUnavailable(
                        "network connection failed",
                        exception);
                default -> throw InterviewScoringException.unexpected(exception);
            }
        }
    }

    private ScoringOutcome readOutcome(ChatResponse response, int durationMs) {
        if (response == null) {
            throw InterviewScoringException.malformedOutput("empty response", null);
        }
        String modelOutput = GeminiAdapterSupport.extractModelOutput(response);
        if (modelOutput == null || modelOutput.isBlank()) {
            throw InterviewScoringException.malformedOutput("empty model output", null);
        }

        GeneratedScoringResult result;
        try {
            result = jsonMapper.readerFor(GeneratedScoringResult.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(GeminiAdapterSupport.stripCodeFences(modelOutput));
        } catch (JacksonException exception) {
            log.warn(
                    "Gemini interview scoring output did not match prompt version {}",
                    properties.scoringPromptVersion());
            throw InterviewScoringException.malformedOutput(
                    "output does not match prompt version "
                            + properties.scoringPromptVersion(),
                    exception);
        }

        return new ScoringOutcome(
                result,
                GeminiAdapterSupport.extractModelName(response, properties.model()),
                properties.scoringPromptVersion(),
                GeminiAdapterSupport.extractTotalTokens(response),
                durationMs);
    }
}
