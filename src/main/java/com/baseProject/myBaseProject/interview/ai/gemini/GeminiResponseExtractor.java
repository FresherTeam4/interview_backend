package com.baseProject.myBaseProject.interview.ai.gemini;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public final class GeminiResponseExtractor {

    private GeminiResponseExtractor() {
    }

    public static String extractModelOutput(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return null;
        }
        return generation.getOutput().getText();
    }

    public static String stripCodeFences(String value) {
        if (value == null) {
            return "";
        }
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

    public static String extractModelName(ChatResponse response, String defaultModel) {
        if (response == null || response.getMetadata() == null) {
            return defaultModel;
        }
        String responseModel = response.getMetadata().getModel();
        return (responseModel == null || responseModel.isBlank()) ? defaultModel : responseModel;
    }

    public static Integer extractTotalTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return null;
        }
        Integer totalTokens = response.getMetadata().getUsage().getTotalTokens();
        return (totalTokens == null || totalTokens == 0) ? null : totalTokens;
    }
}
