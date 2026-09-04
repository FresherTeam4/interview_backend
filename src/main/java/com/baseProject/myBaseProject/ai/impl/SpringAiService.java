package com.baseProject.myBaseProject.ai.impl;

import com.baseProject.myBaseProject.ai.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiService implements AiService {

    private final ChatClient chatClient;

    @Override
    public String generateText(String prompt) {
        log.info("Generating text with AI, prompt length: {} chars", prompt != null ? prompt.length() : 0);

        return chatClient.prompt(prompt).call().content();
    }

    @Override
    public <T> T generateStructured(String prompt, Map<String, Object> params, Class<T> responseClass) {
        log.info("Generating structured AI output for type: {}", responseClass.getSimpleName());

        var outputConverter = new BeanOutputConverter<>(responseClass);
        String finalPrompt = buildPromptString(prompt, params, outputConverter.getFormat());

        ChatResponse response = chatClient.prompt(finalPrompt).call().chatResponse();
        String responseText = extractResponseText(response);

        return outputConverter.convert(responseText);
    }

    @Override
    public <T> T generateStructuredWithImages(String prompt, Map<String, Object> params, List<byte[]> imageBytesList, Class<T> responseClass) {
        log.info("Generating structured AI vision output for type: {}, images: {}",
                responseClass.getSimpleName(), imageBytesList != null ? imageBytesList.size() : 0);

        var outputConverter = new BeanOutputConverter<>(responseClass);
        String instructionText = buildPromptString(prompt, params, outputConverter.getFormat());

        List<Media> mediaList = new ArrayList<>();
        if (imageBytesList != null) {
            for (byte[] imageBytes : imageBytesList) {
                mediaList.add(new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)));
            }
        }

        UserMessage userMessage = UserMessage.builder()
                .text(instructionText)
                .media(mediaList)
                .build();

        ChatResponse response = chatClient.prompt(new Prompt(List.of(userMessage))).call().chatResponse();
        String responseText = extractResponseText(response);

        return outputConverter.convert(responseText);
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
