package com.baseProject.myBaseProject.interview.ai.gemini;

import com.baseProject.myBaseProject.config.InterviewAiConfig;
import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.ai.GeneratedScript;
import com.baseProject.myBaseProject.interview.ai.InterviewQuestionGenerator;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.ScriptGenerationOutcome;
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
public class GeminiInterviewQuestionGenerator implements InterviewQuestionGenerator {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final AiProperties credentialProperties;
    private final InterviewAiProperties properties;
    private final JsonMapper jsonMapper;
    private final ObjectProvider<GoogleGenAiChatModel> chatModelProvider;
    private final String promptTemplate;
    private final String responseSchema;

    public GeminiInterviewQuestionGenerator(
            AiProperties credentialProperties,
            InterviewAiProperties properties,
            JsonMapper jsonMapper,
            ResourceLoader resourceLoader,
            @Qualifier(InterviewAiConfig.SCRIPT_CHAT_MODEL)
            ObjectProvider<GoogleGenAiChatModel> chatModelProvider) {
        this.credentialProperties = credentialProperties;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.chatModelProvider = chatModelProvider;

        String version = properties.scriptPromptVersion();
        this.promptTemplate = readTextResource(resourceLoader,
                "classpath:ai/interview-script-prompt-%s.txt".formatted(version));
        String schemaContract = readTextResource(resourceLoader,
                "classpath:ai/interview-script-schema-%s.json".formatted(version));
        this.responseSchema = toSpringAiSchema(schemaContract);
    }

    @Override
    public ScriptGenerationOutcome generate(ScriptGenerationInput input) {
        if (!credentialProperties.hasApiKey()) {
            throw ScriptGenerationException.noApiKey();
        }

        Prompt request = buildRequest(input);
        long startedAtNanos = System.nanoTime();
        ChatResponse response = call(request);
        int durationMs = Math.toIntExact(
                Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis());
        return readOutcome(response, durationMs);
    }

    private Prompt buildRequest(ScriptGenerationInput input) {
        String contextJson = writeInput(input);
        SystemMessage instructions = new SystemMessage(promptTemplate);
        UserMessage context = UserMessage.builder()
                .text("<UNTRUSTED_INTERVIEW_CONTEXT>\n"
                        + contextJson
                        + "\n</UNTRUSTED_INTERVIEW_CONTEXT>")
                .build();
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                .model(properties.model())
                .outputSchema(responseSchema)
                .build();
        return new Prompt(List.of(instructions, context), options);
    }

    private String writeInput(ScriptGenerationInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("languageCode", input.languageCode());
        payload.put("difficulty", input.difficulty());
        payload.put("questionCount", input.questionCount());
        payload.put("generationSeed", input.generationSeed());
        payload.put("profile", jsonMapper.readTree(input.profile().toString()));
        payload.put("jobDescription", input.jobDescription());
        payload.put("excludedQuestionSignatures",
                input.excludedQuestionSignatures().stream().sorted().toList());
        return jsonMapper.writeValueAsString(payload);
    }

    private ChatResponse call(Prompt request) {
        try {
            return chatModelProvider.getObject().call(request);
        } catch (RuntimeException exception) {
            ClientException clientError = findCause(exception, ClientException.class);
            if (clientError != null) {
                if (clientError.code() == 408) {
                    throw ScriptGenerationException.timeout(exception);
                }
                if (clientError.code() == 429) {
                    log.warn("Gemini rate-limited interview script generation");
                    throw ScriptGenerationException.providerUnavailable("HTTP 429", exception);
                }
                if (clientError.code() == 401 || clientError.code() == 403) {
                    log.error("Gemini credential rejected for interview script generation");
                    throw ScriptGenerationException.noApiKey();
                }
                log.error("Gemini rejected interview script request with HTTP {}", clientError.code());
                throw ScriptGenerationException.unexpected(exception);
            }

            ServerException serverError = findCause(exception, ServerException.class);
            if (serverError != null) {
                log.warn("Gemini returned HTTP {} during interview script generation",
                        serverError.code());
                throw ScriptGenerationException.providerUnavailable(
                        "HTTP " + serverError.code(), exception);
            }

            ApiException apiError = findCause(exception, ApiException.class);
            if (apiError != null) {
                log.warn("Gemini returned HTTP {} during interview script generation",
                        apiError.code());
                throw ScriptGenerationException.providerUnavailable(
                        "HTTP " + apiError.code(), exception);
            }

            if (hasTimeoutCause(exception)) {
                throw ScriptGenerationException.timeout(exception);
            }
            if (findCause(exception, GenAiIOException.class) != null) {
                throw ScriptGenerationException.providerUnavailable(
                        "network connection failed", exception);
            }
            throw ScriptGenerationException.unexpected(exception);
        }
    }

    private ScriptGenerationOutcome readOutcome(ChatResponse response, int durationMs) {
        if (response == null) {
            throw ScriptGenerationException.malformedOutput("empty response", null);
        }

        Generation generation = response.getResult();
        String modelOutput = generation == null || generation.getOutput() == null
                ? null
                : generation.getOutput().getText();
        if (modelOutput == null || modelOutput.isBlank()) {
            throw ScriptGenerationException.malformedOutput("empty model output", null);
        }

        GeneratedScript script;
        try {
            script = jsonMapper.readerFor(GeneratedScript.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(stripCodeFences(modelOutput));
        } catch (JacksonException exception) {
            log.warn("Gemini interview script output did not match prompt version {}",
                    properties.scriptPromptVersion());
            throw ScriptGenerationException.malformedOutput(
                    "output does not match prompt version " + properties.scriptPromptVersion(),
                    exception);
        }

        String responseModel = response.getMetadata() == null
                ? null
                : response.getMetadata().getModel();
        Integer totalTokens = response.getMetadata() == null
                || response.getMetadata().getUsage() == null
                ? null
                : response.getMetadata().getUsage().getTotalTokens();
        return new ScriptGenerationOutcome(
                script,
                responseModel == null || responseModel.isBlank()
                        ? properties.model()
                        : responseModel,
                properties.scriptPromptVersion(),
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
