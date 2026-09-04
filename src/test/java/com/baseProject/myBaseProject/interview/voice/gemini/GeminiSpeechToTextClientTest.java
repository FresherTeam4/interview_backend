package com.baseProject.myBaseProject.interview.voice.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.exception.SpeechTranscriptionException;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionInput;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionOutcome;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

class GeminiSpeechToTextClientTest {

    @Test
    void validJsonReturnsFaithfulTranscriptAndProviderModel() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("{\"transcript\":\"  Nội dung đã nói  \"}");
        when(response.modelVersion()).thenReturn(Optional.of("gemini-test-version"));

        TranscriptionOutcome outcome = adapter("key").readOutcome(response);

        assertThat(outcome.rawText()).isEqualTo("Nội dung đã nói");
        assertThat(outcome.provider()).isEqualTo("gemini-test-version");
        assertThat(outcome.confidence()).isNull();
    }

    @Test
    void malformedProviderJsonIsRejected() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn("not-json");

        assertThatThrownBy(() -> adapter("key").readOutcome(response))
                .isInstanceOf(SpeechTranscriptionException.class)
                .extracting(exception -> ((SpeechTranscriptionException) exception).getReason())
                .isEqualTo(SpeechTranscriptionException.Reason.MALFORMED_OUTPUT);
    }

    @Test
    void missingCredentialFailsBeforeResolvingProviderClient() {
        assertThatThrownBy(() -> adapter("").transcribe(
                        new TranscriptionInput(new byte[]{1}, "audio/webm", "vi-VN")))
                .isInstanceOf(SpeechTranscriptionException.class)
                .extracting(exception -> ((SpeechTranscriptionException) exception).getReason())
                .isEqualTo(SpeechTranscriptionException.Reason.MISSING_CREDENTIAL);
    }

    private GeminiSpeechToTextClient adapter(String apiKey) {
        AiProperties credentials = new AiProperties(
                apiKey,
                "gemini-test",
                "v2",
                25_000);
        VoiceProperties voice = new VoiceProperties(
                15_728_640,
                300_000,
                30,
                true,
                "gemini-test",
                "v1",
                7_000,
                30);
        @SuppressWarnings("unchecked")
        ObjectProvider<Client> provider = mock(ObjectProvider.class);
        return new GeminiSpeechToTextClient(
                credentials,
                voice,
                JsonMapper.builder().build(),
                new DefaultResourceLoader(),
                provider);
    }
}
