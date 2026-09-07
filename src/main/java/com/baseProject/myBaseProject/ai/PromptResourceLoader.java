package com.baseProject.myBaseProject.ai;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class PromptResourceLoader {

    private PromptResourceLoader() {
    }

    public static String loadRequired(String path) throws IOException {
        String prompt = new ClassPathResource(path)
                .getContentAsString(StandardCharsets.UTF_8)
                .strip();
        if (prompt.isEmpty()) {
            throw new IllegalStateException("Prompt resource is empty: " + path);
        }

        return prompt;
    }
}
