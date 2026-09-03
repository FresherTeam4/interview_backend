package com.baseProject.myBaseProject.ai.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;

import java.net.http.HttpTimeoutException;
import java.util.List;

class GeminiAdapterSupportTest {

    @Test
    void classifiesProviderFailuresConsistently() {
        assertThat(GeminiAdapterSupport.classify(
                        new ClientException(408, "DEADLINE_EXCEEDED", "timeout")).kind())
                .isEqualTo(GeminiAdapterSupport.FailureKind.TIMEOUT);
        assertThat(GeminiAdapterSupport.classify(
                        new ClientException(429, "RESOURCE_EXHAUSTED", "quota")).kind())
                .isEqualTo(GeminiAdapterSupport.FailureKind.RATE_LIMITED);
        assertThat(GeminiAdapterSupport.classify(
                        new ClientException(401, "UNAUTHENTICATED", "credential")).kind())
                .isEqualTo(GeminiAdapterSupport.FailureKind.CREDENTIAL_REJECTED);
        assertThat(GeminiAdapterSupport.classify(
                        new ServerException(503, "UNAVAILABLE", "provider down")).kind())
                .isEqualTo(GeminiAdapterSupport.FailureKind.SERVER_ERROR);
        assertThat(GeminiAdapterSupport.classify(
                        new RuntimeException(new HttpTimeoutException("timeout"))).kind())
                .isEqualTo(GeminiAdapterSupport.FailureKind.TIMEOUT);
        assertThat(GeminiAdapterSupport.classify(
                        new GenAiIOException("network")).kind())
                .isEqualTo(GeminiAdapterSupport.FailureKind.NETWORK_ERROR);
    }

    @Test
    void stripsOptionalMarkdownCodeFence() {
        assertThat(GeminiAdapterSupport.stripCodeFences("```json\n{\"ok\":true}\n```"))
                .isEqualTo("{\"ok\":true}");
        assertThat(GeminiAdapterSupport.stripCodeFences(" {\"ok\":true} "))
                .isEqualTo("{\"ok\":true}");
    }

    @Test
    void safelyReadsIncompleteResponseMetadata() {
        ChatResponse response = new ChatResponse(List.of());

        assertThat(GeminiAdapterSupport.extractModelOutput(response)).isNull();
        assertThat(GeminiAdapterSupport.extractModelName(response, "configured-model"))
                .isEqualTo("configured-model");
        assertThat(GeminiAdapterSupport.extractTotalTokens(response)).isNull();
    }
}
