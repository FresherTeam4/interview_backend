package com.baseProject.myBaseProject.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Cấu hình đa model AI (AI Fallback).
 * - Primary: OpenAI (GPT-4o)
 * - Fallback: Google GenAI (Gemini)
 */
@Configuration
public class AiConfig {

    @Bean("primaryChatClient")
    @Primary
    public ChatClient primaryChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    @Bean("fallbackChatClient")
    public ChatClient fallbackChatClient(GoogleGenAiChatModel geminiChatModel) {
        return ChatClient.builder(geminiChatModel).build();
    }
}
