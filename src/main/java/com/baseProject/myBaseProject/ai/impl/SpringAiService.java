package com.baseProject.myBaseProject.ai.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.exception.AiException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiService implements AiService {

    private final ChatClient chatClient;

    @Override
    public String generateText(String prompt) {
        log.info("Generating text with AI, prompt length: {} chars", prompt != null ? prompt.length() : 0);
        try {
            return chatClient.prompt(prompt).call().content();
        } catch (RuntimeException e) {
            throw translateAiException(e);
        }
    }

    @Override
    public <T> T generateStructured(String prompt, Map<String, Object> params, Class<T> responseClass) {
        log.info("Generating structured AI output for type: {}", responseClass.getSimpleName());

        var outputConverter = new BeanOutputConverter<>(responseClass);
        String finalPrompt = buildPromptString(prompt, params, outputConverter.getFormat());

        ChatResponse response = executeCall(new Prompt(finalPrompt));
        String responseText = extractResponseText(response);

        return parseStructuredOutput(responseText, outputConverter, responseClass);
    }

    @Override
    public <T> T generateStructuredWithPdf(String prompt, Map<String, Object> params,
                                           byte[] pdfBytes, Class<T> responseClass) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF content must not be empty");
        }

        log.info("Generating structured AI output from PDF for type: {}, size: {} KB",
                responseClass.getSimpleName(), pdfBytes.length / 1024);

        var outputConverter = new BeanOutputConverter<>(responseClass);
        String instructionText = buildPromptString(prompt, params, outputConverter.getFormat());

        Media pdf = Media.builder()
                .mimeType(MediaType.APPLICATION_PDF)
                .data(pdfBytes)
                .name("candidate-cv.pdf")
                .build();

        UserMessage userMessage = UserMessage.builder()
                .text(instructionText)
                .media(pdf)
                .build();

        ChatResponse response = executeCall(new Prompt(List.of(userMessage)));
        String responseText = extractResponseText(response);

        return parseStructuredOutput(responseText, outputConverter, responseClass);
    }

    private ChatResponse executeCall(Prompt prompt) {
        try {
            return chatClient.prompt(prompt).call().chatResponse();
        } catch (RuntimeException e) {
            throw translateAiException(e);
        }
    }

    private <T> T parseStructuredOutput(String responseText, BeanOutputConverter<T> outputConverter, Class<T> responseClass) {
        if (responseText == null || responseText.isBlank()) {
            throw new AiException(ErrorCode.AI_MALFORMED_OUTPUT, "Empty AI response");
        }
        try {
            // bóc markdown code block nếu mô hình trả về dạng ```json
            String cleaned = stripCodeFences(responseText);
            return outputConverter.convert(cleaned);
        } catch (Exception e) {
            log.error("Failed to parse AI output into {}: {}", responseClass.getSimpleName(), responseText, e);
            throw new AiException(ErrorCode.AI_MALFORMED_OUTPUT, "Could not map AI response to " + responseClass.getSimpleName(), e);
        }
    }

    // dịch ngoại lệ từ tầng hạ tầng SDK sang ngoại lệ nghiệp vụ AiException
    private AiException translateAiException(Throwable e) {
        if (e instanceof AiException aiEx) {
            return aiEx;
        }

        RestClientResponseException restError = findCause(e, RestClientResponseException.class);
        if (restError != null) {
            int statusCode = restError.getStatusCode().value();
            if (statusCode == 429) {
                log.warn("AI service rate limited: HTTP 429");
                return new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI rate limit reached (HTTP 429)", e);
            }
            if (statusCode == 401 || statusCode == 403) {
                log.error("AI service authentication failed: HTTP {}", statusCode);
                return new AiException(ErrorCode.AI_CONFIG_ERROR, "AI authentication failed (HTTP " + statusCode + ")", e);
            }
            if (statusCode == 408 || statusCode == 504) {
                return new AiException(ErrorCode.AI_TIMEOUT, e);
            }
            if (statusCode >= 500) {
                log.warn("AI service returned HTTP {}", statusCode);
                return new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI provider returned HTTP " + statusCode, e);
            }
            log.error("AI service rejected request with HTTP {}", statusCode);
            return new AiException(ErrorCode.AI_ERROR, "AI service error (HTTP " + statusCode + ")", e);
        }

        if (isTimeout(e)) {
            return new AiException(ErrorCode.AI_TIMEOUT, e);
        }

        if (findCause(e, ConnectException.class) != null || findCause(e, UnknownHostException.class) != null) {
            return new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, "Cannot connect to AI service", e);
        }

        log.error("Unexpected error invoking AI service", e);
        return new AiException(ErrorCode.AI_ERROR, e);
    }

    private static String stripCodeFences(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        String content = firstLineEnd < 0 ? "" : trimmed.substring(firstLineEnd + 1);
        return (content.endsWith("```")
                ? content.substring(0, content.length() - 3)
                : content).strip();
    }

    private static boolean isTimeout(Throwable throwable) {
        return findCause(throwable, TimeoutException.class) != null
                || findCause(throwable, SocketTimeoutException.class) != null
                || findCause(throwable, HttpTimeoutException.class) != null
                || findCause(throwable, InterruptedIOException.class) != null;
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

    private String buildPromptString(String prompt, Map<String, Object> params, String format) {
        Map<String, Object> merged = new HashMap<>();
        if (params != null) {
            merged.putAll(params);
        }

        if (prompt != null && prompt.contains("{format}")) {
            merged.put("format", format);
            PromptTemplate template = new PromptTemplate(prompt);
            return template.render(merged);
        } else {
            String rendered = (merged.isEmpty() || prompt == null) ? (prompt != null ? prompt : "") : new PromptTemplate(prompt).render(merged);
            return rendered + "\n\n" + format;
        }
    }

    private String extractResponseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "{}";
        }
        return response.getResult().getOutput().getText();
    }
}
