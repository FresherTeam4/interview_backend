package com.baseProject.myBaseProject.config;

import com.baseProject.myBaseProject.config.properites.GeminiLiveProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RealtimeConfig {

    @Bean("geminiLiveRestClient")
    public RestClient geminiLiveRestClient(GeminiLiveProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory);

        // Cho phép ứng dụng khởi động khi realtime bị tắt; provider sẽ kiểm tra key lúc cấp grant.
        if (!properties.apiKey().isBlank()) {
            builder.defaultHeader("x-goog-api-key", properties.apiKey());
        }
        return builder.build();
    }
}
