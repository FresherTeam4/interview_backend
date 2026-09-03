package com.baseProject.myBaseProject.interview.ai.gemini;

import com.baseProject.myBaseProject.config.InterviewAiConfig;
import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpDecisionOutcome;
import com.baseProject.myBaseProject.interview.ai.model.FollowUpTurnContext;
import com.baseProject.myBaseProject.interview.ai.model.GeneratedFollowUpDecision;
import com.baseProject.myBaseProject.interview.ai.port.InterviewFollowUpDecider;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiInterviewFollowUpDecider implements InterviewFollowUpDecider {

    private final AiProperties credentialProperties;
    private final InterviewAiProperties properties;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<GoogleGenAiChatModel> chatModelProvider;
    private final String promptTemplate;
    private final String responseSchema;

    public GeminiInterviewFollowUpDecider(
            AiProperties credentialProperties,
            InterviewAiProperties properties,
            JsonMapper jsonMapper,
            ResourceLoader resourceLoader,
            @Qualifier(InterviewAiConfig.FOLLOW_UP_CHAT_MODEL)
            ObjectProvider<GoogleGenAiChatModel> chatModelProvider) {
        this.credentialProperties = credentialProperties;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.chatModelProvider = chatModelProvider;

        String version = properties.followUpPromptVersion();
        this.promptTemplate = AiResourceReader.readClasspathResource(resourceLoader,
                "classpath:ai/interview-follow-up-prompt-%s.txt".formatted(version));
        String schemaContract = AiResourceReader.readClasspathResource(resourceLoader,
                "classpath:ai/interview-follow-up-schema-%s.json".formatted(version));
        this.responseSchema = GeminiSchemaConverter.toSpringAiSchema(jsonMapper, schemaContract);
    }

    @Override
    public FollowUpDecisionOutcome decide(FollowUpDecisionInput input) {
        if (!credentialProperties.hasApiKey()) {
            throw FollowUpDecisionException.noApiKey();
        }

        Prompt request = buildRequest(input);
        long startedAtNanos = System.nanoTime();
        ChatResponse response = call(request);
        int durationMs = Math.toIntExact(
                Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis());
        return readOutcome(response, durationMs);
    }

    private Prompt buildRequest(FollowUpDecisionInput input) {
        SystemMessage instructions = new SystemMessage(promptTemplate);
        UserMessage context = UserMessage.builder()
                .text("<UNTRUSTED_FOLLOW_UP_CONTEXT>\n"
                        + writeInput(input)
                        + "\n</UNTRUSTED_FOLLOW_UP_CONTEXT>")
                .build();
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(properties.model())
                .outputSchema(responseSchema)
                .build();
        return new Prompt(List.of(instructions, context), options);
    }

    private String writeInput(FollowUpDecisionInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("languageCode", input.languageCode());
        payload.put("baseQuestion", input.baseQuestion());
        payload.put("topic", input.topic());
        payload.put("competency", input.competency());
        payload.put("relevantProfileContext",
                jsonMapper.readTree(input.relevantProfileContext().toString()));
        payload.put("relevantJobDescriptionExcerpt", input.relevantJobDescriptionExcerpt());
        payload.put("currentQuestionTurns", input.currentQuestionTurns().stream()
                .map(this::toTurnPayload)
                .toList());
        payload.put("candidateAnswer", input.candidateAnswer());
        payload.put("remainingQuestionFollowUps", input.remainingQuestionFollowUps());
        payload.put("remainingSessionFollowUps", input.remainingSessionFollowUps());
        return jsonMapper.writeValueAsString(payload);
    }

    private Map<String, Object> toTurnPayload(FollowUpTurnContext turn) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("role", turn.role());
        payload.put("content", turn.content());
        payload.put("isFollowUp", turn.followUp());
        payload.put("followUpDepth", turn.followUpDepth());
        return payload;
    }

    private ChatResponse call(Prompt request) {
        try {
            return chatModelProvider.getObject().call(request);
        } catch (RuntimeException exception) {
            GeminiFailureClassifier.ClassifiedFailure failure = GeminiFailureClassifier.classify(exception);
            switch (failure.kind()) {
                case TIMEOUT -> throw FollowUpDecisionException.timeout(exception);
                case RATE_LIMITED -> {
                    log.warn("Gemini rate-limited interview follow-up decision");
                    throw FollowUpDecisionException.providerUnavailable("HTTP 429", exception);
                }
                case CREDENTIAL_REJECTED -> {
                    log.error("Gemini credential rejected for interview follow-up decision");
                    throw FollowUpDecisionException.noApiKey();
                }
                case CLIENT_ERROR -> {
                    log.error("Gemini rejected interview follow-up request with HTTP {}", failure.statusCode());
                    throw FollowUpDecisionException.unexpected(exception);
                }
                case SERVER_ERROR, API_ERROR -> {
                    log.warn("Gemini returned HTTP {} during interview follow-up decision", failure.statusCode());
                    throw FollowUpDecisionException.providerUnavailable("HTTP " + failure.statusCode(), exception);
                }
                case NETWORK_ERROR -> throw FollowUpDecisionException.providerUnavailable("network connection failed", exception);
                default -> throw FollowUpDecisionException.unexpected(exception);
            }
        }
    }

    private FollowUpDecisionOutcome readOutcome(ChatResponse response, int durationMs) {
        if (response == null) {
            throw FollowUpDecisionException.malformedOutput("empty response", null);
        }

        String modelOutput = GeminiResponseExtractor.extractModelOutput(response);
        if (modelOutput == null || modelOutput.isBlank()) {
            throw FollowUpDecisionException.malformedOutput("empty model output", null);
        }

        GeneratedFollowUpDecision result;
        try {
            result = jsonMapper.readerFor(GeneratedFollowUpDecision.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(GeminiResponseExtractor.stripCodeFences(modelOutput));
        } catch (JacksonException exception) {
            log.warn("Gemini interview follow-up output did not match prompt version {}",
                    properties.followUpPromptVersion());
            throw FollowUpDecisionException.malformedOutput(
                    "output does not match prompt version " + properties.followUpPromptVersion(),
                    exception);
        }

        String responseModel = GeminiResponseExtractor.extractModelName(response, properties.model());
        Integer totalTokens = GeminiResponseExtractor.extractTotalTokens(response);
        return new FollowUpDecisionOutcome(
                result,
                responseModel,
                properties.followUpPromptVersion(),
                totalTokens,
                durationMs);
    }
}
