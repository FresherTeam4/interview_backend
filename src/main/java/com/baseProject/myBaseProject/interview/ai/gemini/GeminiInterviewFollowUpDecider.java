package com.baseProject.myBaseProject.interview.ai.gemini;

import com.baseProject.myBaseProject.config.InterviewAiConfig;
import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.exception.FollowUpDecisionException;
import com.baseProject.myBaseProject.interview.ai.FollowUpDecisionInput;
import com.baseProject.myBaseProject.interview.ai.FollowUpDecisionOutcome;
import com.baseProject.myBaseProject.interview.ai.FollowUpTurnContext;
import com.baseProject.myBaseProject.interview.ai.GeneratedFollowUpDecision;
import com.baseProject.myBaseProject.interview.ai.InterviewFollowUpDecider;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class GeminiInterviewFollowUpDecider implements InterviewFollowUpDecider {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

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
        this.promptTemplate = readTextResource(resourceLoader,
                "classpath:ai/interview-follow-up-prompt-%s.txt".formatted(version));
        String schemaContract = readTextResource(resourceLoader,
                "classpath:ai/interview-follow-up-schema-%s.json".formatted(version));
        this.responseSchema = toSpringAiSchema(schemaContract);
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
            ClientException clientError = findCause(exception, ClientException.class);
            if (clientError != null) {
                if (clientError.code() == 408) {
                    throw FollowUpDecisionException.timeout(exception);
                }
                if (clientError.code() == 429) {
                    log.warn("Gemini rate-limited interview follow-up decision");
                    throw FollowUpDecisionException.providerUnavailable("HTTP 429", exception);
                }
                if (clientError.code() == 401 || clientError.code() == 403) {
                    log.error("Gemini credential rejected for interview follow-up decision");
                    throw FollowUpDecisionException.noApiKey();
                }
                log.error("Gemini rejected interview follow-up request with HTTP {}",
                        clientError.code());
                throw FollowUpDecisionException.unexpected(exception);
            }

            ServerException serverError = findCause(exception, ServerException.class);
            if (serverError != null) {
                log.warn("Gemini returned HTTP {} during interview follow-up decision",
                        serverError.code());
                throw FollowUpDecisionException.providerUnavailable(
                        "HTTP " + serverError.code(), exception);
            }

            ApiException apiError = findCause(exception, ApiException.class);
            if (apiError != null) {
                log.warn("Gemini returned HTTP {} during interview follow-up decision",
                        apiError.code());
                throw FollowUpDecisionException.providerUnavailable(
                        "HTTP " + apiError.code(), exception);
            }

            if (hasTimeoutCause(exception)) {
                throw FollowUpDecisionException.timeout(exception);
            }
            if (findCause(exception, GenAiIOException.class) != null) {
                throw FollowUpDecisionException.providerUnavailable(
                        "network connection failed", exception);
            }
            throw FollowUpDecisionException.unexpected(exception);
        }
    }

    private FollowUpDecisionOutcome readOutcome(ChatResponse response, int durationMs) {
        if (response == null) {
            throw FollowUpDecisionException.malformedOutput("empty response", null);
        }

        Generation generation = response.getResult();
        String modelOutput = generation == null || generation.getOutput() == null
                ? null
                : generation.getOutput().getText();
        if (modelOutput == null || modelOutput.isBlank()) {
            throw FollowUpDecisionException.malformedOutput("empty model output", null);
        }

        GeneratedFollowUpDecision result;
        try {
            result = jsonMapper.readerFor(GeneratedFollowUpDecision.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(stripCodeFences(modelOutput));
        } catch (JacksonException exception) {
            log.warn("Gemini interview follow-up output did not match prompt version {}",
                    properties.followUpPromptVersion());
            throw FollowUpDecisionException.malformedOutput(
                    "output does not match prompt version " + properties.followUpPromptVersion(),
                    exception);
        }

        String responseModel = response.getMetadata() == null
                ? null
                : response.getMetadata().getModel();
        Integer totalTokens = response.getMetadata() == null
                || response.getMetadata().getUsage() == null
                ? null
                : response.getMetadata().getUsage().getTotalTokens();
        return new FollowUpDecisionOutcome(
                result,
                responseModel == null || responseModel.isBlank()
                        ? properties.model()
                        : responseModel,
                properties.followUpPromptVersion(),
                totalTokens == null || totalTokens == 0 ? null : totalTokens,
                durationMs);
    }

    private String toSpringAiSchema(String schemaContract) {
        Map<String, Object> schema = jsonMapper.readValue(schemaContract, JSON_OBJECT);
        normalizeSchema(schema);
        return jsonMapper.writeValueAsString(schema);
    }

    @SuppressWarnings("unchecked")
    private static void normalizeSchema(Map<String, Object> schema) {
        schema.remove("additionalProperties");

        Object type = schema.get("type");
        if (type instanceof List<?> types) {
            List<Object> nonNullTypes = new ArrayList<>();
            boolean nullable = false;
            for (Object value : types) {
                if ("null".equals(value)) {
                    nullable = true;
                } else {
                    nonNullTypes.add(value);
                }
            }
            schema.put("type", nonNullTypes.size() == 1 ? nonNullTypes.get(0) : nonNullTypes);
            if (nullable) {
                schema.put("nullable", true);
            }
        }

        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> propertyMap) {
            for (Object child : propertyMap.values()) {
                if (child instanceof Map<?, ?> childSchema) {
                    normalizeSchema((Map<String, Object>) childSchema);
                }
            }
        }
        Object items = schema.get("items");
        if (items instanceof Map<?, ?> itemSchema) {
            normalizeSchema((Map<String, Object>) itemSchema);
        }
    }

    private static String stripCodeFences(String value) {
        String trimmed = value.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        String content = firstLineEnd < 0 ? "" : trimmed.substring(firstLineEnd + 1);
        return (content.endsWith("```")
                ? content.substring(0, content.length() - 3)
                : content).strip();
    }

    private static boolean hasTimeoutCause(Throwable throwable) {
        return findCause(throwable, InterruptedIOException.class) != null
                || findCause(throwable, HttpTimeoutException.class) != null
                || findCause(throwable, TimeoutException.class) != null;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return null;
    }

    private static String readTextResource(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read AI contract resource " + location, exception);
        }
    }
}
