package com.baseProject.myBaseProject.config;

import com.baseProject.myBaseProject.config.properites.AiProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration(proxyBeanMethods = false)
public class CvParserAiConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    Client cvParserGenAiClient(AiProperties properties) {
        if (!properties.hasApiKey()) {
            throw new IllegalStateException("Thiếu cấu hình app.ai.api-key");
        }

        HttpOptions httpOptions = HttpOptions.builder()
                .timeout(Math.toIntExact(properties.timeoutMs()))
                .build();

        return Client.builder()
                .apiKey(properties.apiKey())
                .httpOptions(httpOptions)
                .build();
    }

    @Bean
    @Lazy
    GoogleGenAiChatModel cvParserChatModel(Client cvParserGenAiClient,
                                           AiProperties properties) {
        return GoogleGenAiChatModel.builder()
                .genAiClient(cvParserGenAiClient)
                .options(GoogleGenAiChatOptions.builder()
                        .model(properties.model())
                        .build())
                // Timeout 25 giây là ngân sách cho toàn tác vụ; không retry ngầm để tránh
                // một lần parse vượt tiêu chí nghiệp vụ 30 giây.
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
    }
}
