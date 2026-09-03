package com.baseProject.myBaseProject.interview.ai.gemini;

import com.baseProject.myBaseProject.config.InterviewAiConfig;
import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.baseProject.myBaseProject.exception.ScriptGenerationException;
import com.baseProject.myBaseProject.interview.ai.model.GeneratedScript;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationInput;
import com.baseProject.myBaseProject.interview.ai.model.ScriptGenerationOutcome;
import com.baseProject.myBaseProject.interview.ai.port.InterviewQuestionGenerator;

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
public class GeminiInterviewQuestionGenerator implements InterviewQuestionGenerator {

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
        this.promptTemplate = AiResourceReader.readClasspathResource(resourceLoader,
                "classpath:ai/interview-script-prompt-%s.txt".formatted(version));
        String schemaContract = AiResourceReader.readClasspathResource(resourceLoader,
                "classpath:ai/interview-script-schema-%s.json".formatted(version));
        this.responseSchema = GeminiSchemaConverter.toSpringAiSchema(jsonMapper, schemaContract);
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
            GeminiFailureClassifier.ClassifiedFailure failure = GeminiFailureClassifier.classify(exception);
            switch (failure.kind()) {
                case TIMEOUT -> throw ScriptGenerationException.timeout(exception);
                case RATE_LIMITED -> {
                    log.warn("Gemini rate-limited interview script generation");
                    throw ScriptGenerationException.providerUnavailable("HTTP 429", exception);
                }
                case CREDENTIAL_REJECTED -> {
                    log.error("Gemini credential rejected for interview script generation");
                    throw ScriptGenerationException.noApiKey();
                }
                case CLIENT_ERROR -> {
                    log.error("Gemini rejected interview script request with HTTP {}", failure.statusCode());
                    throw ScriptGenerationException.unexpected(exception);
                }
                case SERVER_ERROR, API_ERROR -> {
                    log.warn("Gemini returned HTTP {} during interview script generation", failure.statusCode());
                    throw ScriptGenerationException.providerUnavailable("HTTP " + failure.statusCode(), exception);
                }
                case NETWORK_ERROR -> throw ScriptGenerationException.providerUnavailable("network connection failed", exception);
                default -> throw ScriptGenerationException.unexpected(exception);
            }
        }
    }

    private ScriptGenerationOutcome readOutcome(ChatResponse response, int durationMs) {
        if (response == null) {
            throw ScriptGenerationException.malformedOutput("empty response", null);
        }

        String modelOutput = GeminiResponseExtractor.extractModelOutput(response);
        if (modelOutput == null || modelOutput.isBlank()) {
            throw ScriptGenerationException.malformedOutput("empty model output", null);
        }

        GeneratedScript script;
        try {
            script = jsonMapper.readerFor(GeneratedScript.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(GeminiResponseExtractor.stripCodeFences(modelOutput));
        } catch (JacksonException exception) {
            log.warn("Gemini interview script output did not match prompt version {}",
                    properties.scriptPromptVersion());
            throw ScriptGenerationException.malformedOutput(
                    "output does not match prompt version " + properties.scriptPromptVersion(),
                    exception);
        }

        String responseModel = GeminiResponseExtractor.extractModelName(response, properties.model());
        Integer totalTokens = GeminiResponseExtractor.extractTotalTokens(response);
        return new ScriptGenerationOutcome(
                script,
                responseModel,
                properties.scriptPromptVersion(),
                totalTokens,
                durationMs);
    }
}
