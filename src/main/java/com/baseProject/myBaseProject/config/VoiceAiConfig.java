package com.baseProject.myBaseProject.config;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration(proxyBeanMethods = false)
public class VoiceAiConfig {

    public static final String STT_CLIENT = "voiceSttGenAiClient";

    @Bean(name = STT_CLIENT, destroyMethod = "close")
    @Lazy
    Client voiceSttGenAiClient(
            AiProperties credentialProperties,
            VoiceProperties voiceProperties) {
        if (!credentialProperties.hasApiKey()) {
            throw new IllegalStateException("Thiếu cấu hình app.ai.api-key");
        }
        return Client.builder()
                .apiKey(credentialProperties.apiKey())
                .httpOptions(HttpOptions.builder()
                        .timeout(Math.toIntExact(voiceProperties.sttTimeoutMs()))
                        .build())
                .build();
    }
}
