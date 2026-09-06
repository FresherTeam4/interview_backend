package com.baseProject.myBaseProject.ai.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    private SpringAiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new SpringAiService(chatClient);
    }

    @Test
    void sendsPdfAsApplicationPdfMedia() {
        byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46};
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("{\"value\":\"ok\"}"))));

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.chatResponse()).thenReturn(response);

        PdfResult result = aiService.generateStructuredWithPdf(
                "Extract this CV as JSON. {format}", pdfBytes, PdfResult.class);

        assertThat(result.value()).isEqualTo("ok");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());

        assertThat(promptCaptor.getValue().getUserMessage().getMedia())
                .singleElement()
                .satisfies(media -> {
                    assertThat(media.getMimeType()).isEqualTo(MediaType.APPLICATION_PDF);
                    assertThat(media.getName()).isEqualTo("candidate-cv.pdf");
                    assertThat(media.getDataAsByteArray()).containsExactly(pdfBytes);
                });
    }

    @Test
    void rejectsEmptyPdfBeforeCallingAi() {
        assertThatThrownBy(() -> aiService.generateStructuredWithPdf(
                "Extract this CV", new byte[0], PdfResult.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PDF content must not be empty");

        verifyNoInteractions(chatClient);
    }

    @Test
    void sendsTrustedInstructionsAsSystemMessageAndDataAsUserMessage() {
        ChatResponse response = new ChatResponse(List.of(
                new Generation(new AssistantMessage("{\"value\":\"ok\"}"))));

        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.chatResponse()).thenReturn(response);

        PdfResult result = aiService.generateStructured(
                "Apply style: {styleInstruction}",
                "Candidate data: {candidateData}\n{format}",
                Map.of(
                        "styleInstruction", "Use a professional tone",
                        "candidateData", "Spring Boot experience"),
                PdfResult.class);

        assertThat(result.value()).isEqualTo("ok");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertThat(prompt.getSystemMessage().getText())
                .contains("Use a professional tone")
                .doesNotContain("Spring Boot experience")
                .doesNotContain("\"value\"")
                .doesNotContain("JSON");
        assertThat(prompt.getUserMessage().getText())
                .contains("Spring Boot experience")
                .contains("JSON")
                .contains("\"value\"");
    }

    private record PdfResult(String value) {
    }
}
