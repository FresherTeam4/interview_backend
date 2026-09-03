package com.baseProject.myBaseProject.interview.ai.gemini;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AiResourceReader {

    private AiResourceReader() {
    }

    public static String readClasspathResource(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read AI contract resource " + location, exception);
        }
    }
}
