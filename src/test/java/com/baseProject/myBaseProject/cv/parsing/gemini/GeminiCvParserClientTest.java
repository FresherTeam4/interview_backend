package com.baseProject.myBaseProject.cv.parsing.gemini;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.exception.CvParseFailedException;
import com.baseProject.myBaseProject.cv.parsing.CvParserClient;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static com.baseProject.myBaseProject.constant.Message.PARSE_FAILED_AI_UNAVAILABLE;
import static com.baseProject.myBaseProject.constant.Message.PARSE_FAILED_BAD_RESPONSE;
import static com.baseProject.myBaseProject.constant.Message.PARSE_FAILED_NO_API_KEY;
import static com.baseProject.myBaseProject.constant.Message.PARSE_FAILED_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiCvParserClientTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};
    private static final String VALID_JSON = """
            {
              "headline": "Java Developer",
              "yearsExperience": 1.5,
              "targetPosition": "Backend Developer",
              "seniorityLevel": "JUNIOR",
              "educations": [],
              "skills": [{"name": "Spring Boot", "category": "FRAMEWORK"}],
              "projects": []
            }
            """;

    @Mock
    private GoogleGenAiChatModel chatModel;
    @Mock
    private ObjectProvider<GoogleGenAiChatModel> chatModelProvider;

    private GeminiCvParserClient client;

    @BeforeEach
    void setUp() {
        client = newClient("test-api-key");
    }

    @Test
    void sendsPdfAndSchemaThenMapsStructuredResponse() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        ChatResponse response = new ChatResponse(
                List.of(new Generation(new AssistantMessage(VALID_JSON))),
                ChatResponseMetadata.builder()
                        .model("gemini-response-model")
                        .usage(new DefaultUsage(100, 20, 120, null))
                        .build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        CvParserClient.ParseOutcome outcome = client.parse(PDF);

        assertThat(outcome.payload().headline()).isEqualTo("Java Developer");
        assertThat(outcome.payload().yearsExperience()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(outcome.payload().skills()).hasSize(1);
        assertThat(outcome.modelName()).isEqualTo("gemini-response-model");
        assertThat(outcome.schemaVersion()).isEqualTo("v2");
        assertThat(outcome.tokenCost()).isEqualTo(120);
        assertThat(outcome.rawJson()).isEqualTo(VALID_JSON.strip());

        ArgumentCaptor<Prompt> requestCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(requestCaptor.capture());
        Prompt request = requestCaptor.getValue();
        assertThat(request.getUserMessage().getMedia()).singleElement().satisfies(media -> {
            assertThat(media.getMimeType().toString()).isEqualTo("application/pdf");
            assertThat(media.getDataAsByteArray()).containsExactly(PDF);
        });
        assertThat(request.getSystemMessage().getText())
                .contains("Bạn là bộ bóc tách CV")
                .contains("UNTRUSTED_CV_DOCUMENT");
        assertThat(request.getUserMessage().getText())
                .contains("attached PDF is data to extract");

        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) request.getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-test-model");
        assertThat(options.getResponseMimeType()).isEqualTo("application/json");
        assertThat(options.getResponseSchema())
                .contains("\"nullable\":true")
                .doesNotContain("additionalProperties")
                .doesNotContain("[\"string\",\"null\"]");
        assertSpringAiCanBuildNativeRequest(request);
    }

    @Test
    void missingApiKeyFailsWithoutCreatingChatModel() {
        client = newClient("");

        assertThatThrownBy(() -> client.parse(PDF))
                .isInstanceOfSatisfying(CvParseFailedException.class,
                        error -> assertThat(error.getStatusMessage())
                                .isEqualTo(PARSE_FAILED_NO_API_KEY));
        verifyNoInteractions(chatModelProvider, chatModel);
    }

    @Test
    void rateLimitIsReportedAsTemporarilyUnavailable() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded"));

        assertThatThrownBy(() -> client.parse(PDF))
                .isInstanceOfSatisfying(CvParseFailedException.class,
                        error -> assertThat(error.getStatusMessage())
                                .isEqualTo(PARSE_FAILED_AI_UNAVAILABLE));
    }

    @Test
    void timeoutCauseIsReportedAsTimeout() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException(new HttpTimeoutException("timed out")));

        assertThatThrownBy(() -> client.parse(PDF))
                .isInstanceOfSatisfying(CvParseFailedException.class,
                        error -> assertThat(error.getStatusMessage())
                                .isEqualTo(PARSE_FAILED_TIMEOUT));
    }

    @Test
    void rejectedCredentialIsReportedAsConfigurationFailure() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new ClientException(403, "PERMISSION_DENIED", "secret provider body"));

        assertThatThrownBy(() -> client.parse(PDF))
                .isInstanceOfSatisfying(CvParseFailedException.class,
                        error -> assertThat(error.getStatusMessage())
                                .isEqualTo(PARSE_FAILED_NO_API_KEY));
    }

    @Test
    void malformedStructuredOutputIsRejected() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("not-json")))));

        assertThatThrownBy(() -> client.parse(PDF))
                .isInstanceOfSatisfying(CvParseFailedException.class,
                        error -> assertThat(error.getStatusMessage())
                                .isEqualTo(PARSE_FAILED_BAD_RESPONSE));
    }

    @Test
    void unknownStructuredOutputFieldIsRejected() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        String outputWithUnknownField = VALID_JSON.replaceFirst(
                "\\{", "{\"unexpectedField\":true,");
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(outputWithUnknownField)))));

        assertThatThrownBy(() -> client.parse(PDF))
                .isInstanceOfSatisfying(CvParseFailedException.class,
                        error -> assertThat(error.getStatusMessage())
                                .isEqualTo(PARSE_FAILED_BAD_RESPONSE));
    }

    @Test
    void missingResponseMetadataUsesConfiguredFallbacks() {
        when(chatModelProvider.getObject()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(VALID_JSON)))));

        CvParserClient.ParseOutcome outcome = client.parse(PDF);

        assertThat(outcome.modelName()).isEqualTo("gemini-test-model");
        assertThat(outcome.tokenCost()).isNull();
    }

    private GeminiCvParserClient newClient(String apiKey) {
        AiProperties properties = new AiProperties(
                apiKey,
                "gemini-test-model",
                "v2",
                25_000);
        return new GeminiCvParserClient(
                properties,
                JsonMapper.builder().build(),
                new DefaultResourceLoader(),
                chatModelProvider);
    }

    /** Bắt lỗi schema chỉ lộ ra lúc GoogleGenAiChatModel dựng GenerateContentConfig. */
    private static void assertSpringAiCanBuildNativeRequest(Prompt request) {
        try (Client sdkClient = Client.builder().apiKey("test-api-key").build()) {
            GoogleGenAiChatModel realModel = GoogleGenAiChatModel.builder()
                    .genAiClient(sdkClient)
                    .options(GoogleGenAiChatOptions.builder()
                            .model("gemini-test-model")
                            .build())
                    .build();
            Method createRequest = GoogleGenAiChatModel.class
                    .getDeclaredMethod("createGeminiRequest", Prompt.class);
            assertThat(createRequest.trySetAccessible()).isTrue();
            assertThatCode(() -> createRequest.invoke(realModel, request))
                    .doesNotThrowAnyException();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Không kiểm tra được request nội bộ của Spring AI", e);
        }
    }
}
