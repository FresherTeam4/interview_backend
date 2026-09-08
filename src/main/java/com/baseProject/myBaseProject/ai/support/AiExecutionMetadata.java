package com.baseProject.myBaseProject.ai.support;

import org.springframework.ai.chat.model.ChatModel;

public final class AiExecutionMetadata {
    private static final String UNKNOWN_MODEL = "unknown";

    private AiExecutionMetadata() {
    }

    public static String resolveModelName(ChatModel chatModel) {
        return resolveModelName(null, chatModel);
    }

    // get model name from config
    public static String resolveModelName(String reportedModelName, ChatModel chatModel) {
        String reported = normalizeModelName(reportedModelName);
        if (reported != null) {
            return reported;
        }
        String configured = chatModel == null || chatModel.getOptions() == null
                ? null : normalizeModelName(chatModel.getOptions().getModel());

                return configured == null ? UNKNOWN_MODEL : configured;
    }

    public static int toNonNegativeInt(long value) {
        return (int) Math.min(Math.max(value, 0L), Integer.MAX_VALUE);
    }

    private static String normalizeModelName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
