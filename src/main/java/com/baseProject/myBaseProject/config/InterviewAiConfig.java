package com.baseProject.myBaseProject.config;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.InterviewAiProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration(proxyBeanMethods = false)
public class InterviewAiConfig {

    public static final String SCRIPT_CLIENT = "interviewScriptGenAiClient";
    public static final String SCRIPT_CHAT_MODEL = "interviewScriptChatModel";
    public static final String FOLLOW_UP_CLIENT = "interviewFollowUpGenAiClient";
    public static final String FOLLOW_UP_CHAT_MODEL = "interviewFollowUpChatModel";

    @Bean(name = SCRIPT_CLIENT, destroyMethod = "close")
    @Lazy
    Client interviewScriptGenAiClient(
            AiProperties credentialProperties,
            InterviewAiProperties interviewAiProperties) {
        if (!credentialProperties.hasApiKey()) {
            throw new IllegalStateException("Thiếu cấu hình app.ai.api-key");
        }

        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(interviewAiProperties.scriptTimeoutMs()))
                .build();
        return Client.builder()
                .apiKey(credentialProperties.apiKey())
                .httpOptions(httpOptions)
                .build();
    }

    @Bean(name = SCRIPT_CHAT_MODEL)
    @Lazy
    GoogleGenAiChatModel interviewScriptChatModel(
            @Qualifier(SCRIPT_CLIENT) Client interviewScriptGenAiClient,
            InterviewAiProperties properties) {
        return GoogleGenAiChatModel.builder()
                .genAiClient(interviewScriptGenAiClient)
                .options(GoogleGenAiChatOptions.builder()
                        .model(properties.model())
                        .build())
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }

    @Bean(name = FOLLOW_UP_CLIENT, destroyMethod = "close")
    @Lazy
    Client interviewFollowUpGenAiClient(
            AiProperties credentialProperties,
            InterviewAiProperties interviewAiProperties) {
        if (!credentialProperties.hasApiKey()) {
            throw new IllegalStateException("Thiếu cấu hình app.ai.api-key");
        }

        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(interviewAiProperties.followUpTimeoutMs()))
                .build();
        return Client.builder()
                .apiKey(credentialProperties.apiKey())
                .httpOptions(httpOptions)
                .build();
    }

    @Bean(name = FOLLOW_UP_CHAT_MODEL)
    @Lazy
    GoogleGenAiChatModel interviewFollowUpChatModel(
            @Qualifier(FOLLOW_UP_CLIENT) Client interviewFollowUpGenAiClient,
            InterviewAiProperties properties) {
        return GoogleGenAiChatModel.builder()
                .genAiClient(interviewFollowUpGenAiClient)
                .options(GoogleGenAiChatOptions.builder()
                        .model(properties.model())
                        .build())
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }
}
