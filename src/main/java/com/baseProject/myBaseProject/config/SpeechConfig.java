package com.baseProject.myBaseProject.config;

import com.baseProject.myBaseProject.config.properites.SpeechProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class SpeechConfig {

    @Bean("elevenLabsRestClient")
    public RestClient elevenLabsRestClient(SpeechProperties properties) {
        SpeechProperties.ElevenLabs elevenLabs = properties.providers().elevenlabs();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(elevenLabs.baseUrl().toString())
                .requestFactory(requestFactory);

        // Cho phép ứng dụng khởi động mà không cần API key khi tính năng speech đang tắt.
        if (!elevenLabs.apiKey().isBlank()) {
            builder.defaultHeader("xi-api-key", elevenLabs.apiKey());
        }

        return builder.build();
    }
}
