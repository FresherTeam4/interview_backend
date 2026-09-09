package com.baseProject.myBaseProject.speech.provider.elevenlabs;

import com.baseProject.myBaseProject.config.properites.SpeechProperties;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisRequest;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionRequest;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(OutputCaptureExtension.class)
class ElevenLabsSpeechProviderTest {
    private MockRestServiceServer server;
    private ElevenLabsSpeechProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.elevenlabs.io")
                .defaultHeader("xi-api-key", "test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ElevenLabsSpeechProvider(
                builder.build(), properties(), new ObjectMapper());
    }

    @Test
    void transcribeSendsKnownSessionLanguage() {
        server.expect(once(), requestTo("https://api.elevenlabs.io/v1/speech-to-text"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("xi-api-key", "test-key"))
                .andExpect(header("Content-Type", org.hamcrest.Matchers.startsWith(
                        MediaType.MULTIPART_FORM_DATA_VALUE)))
                .andRespond(withSuccess(
                        """
                                {"text":"日本語の回答です。","language_code":"ja"}
                                """,
                        MediaType.APPLICATION_JSON));

        SpeechTranscriptionResult result = provider.transcribe(
                new SpeechTranscriptionRequest(
                        new byte[]{1, 2}, "answer.webm", "audio/webm", "ja"));

        assertThat(result.text()).isEqualTo("日本語の回答です。");
        assertThat(result.languageCode()).isEqualTo("ja");
        server.verify();
    }

    @Test
    void synthesizeUsesConfiguredModelVoiceAndLanguage() {
        server.expect(once(), requestTo(
                        "https://api.elevenlabs.io/v1/text-to-speech/voice-vi"
                                + "?output_format=mp3_44100_128"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("xi-api-key", "test-key"))
                .andExpect(content().json("""
                        {
                          "text": "Bạn hãy giới thiệu về bản thân.",
                          "model_id": "eleven_flash_v2_5",
                          "language_code": "vi"
                        }
                        """))
                .andRespond(withSuccess(new byte[]{3, 4, 5},
                        MediaType.valueOf("audio/mpeg")));

        SpeechSynthesisResult result = provider.synthesize(
                new SpeechSynthesisRequest(
                        "Bạn hãy giới thiệu về bản thân.", "vi-VN"));

        assertThat(result.audio()).containsExactly(3, 4, 5);
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        server.verify();
    }

    @Test
    void logsStructuredProviderError(CapturedOutput output) {
        server.expect(once(), requestTo(
                        "https://api.elevenlabs.io/v1/text-to-speech/voice-vi"
                                + "?output_format=mp3_44100_128"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("request-id", "request-123")
                        .body("""
                                {
                                  "detail": {
                                    "type": "not_found",
                                    "code": "voice_not_found",
                                    "message": "The specified voice does not exist"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> provider.synthesize(
                new SpeechSynthesisRequest("Xin chào", "vi")))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.SPEECH_PROVIDER_ERROR));
        assertThat(output)
                .contains("httpStatus=404")
                .contains("errorType=not_found")
                .contains("errorCode=voice_not_found")
                .contains("requestId=request-123")
                .contains("message=The specified voice does not exist");
        server.verify();
    }

    private SpeechProperties properties() {
        SpeechProperties.ElevenLabs elevenLabs = new SpeechProperties.ElevenLabs(
                "test-key",
                URI.create("https://api.elevenlabs.io"),
                "scribe_v2",
                "eleven_flash_v2_5",
                "mp3_44100_128",
                Map.of("en", "voice-en", "ja", "voice-ja", "vi", "voice-vi"));
        return new SpeechProperties(
                true,
                "elevenlabs",
                "elevenlabs",
                1024,
                Duration.ofSeconds(5),
                Duration.ofSeconds(45),
                new SpeechProperties.Providers(elevenLabs));
    }
}
