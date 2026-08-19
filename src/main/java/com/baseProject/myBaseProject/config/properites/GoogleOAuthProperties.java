package com.baseProject.myBaseProject.config.properites;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Validated
@ConfigurationProperties(prefix = "app.oauth.google")
public record GoogleOAuthProperties(
        String clientId,

        @NotBlank
        String jwkSetUri,

        @NotEmpty(message = "Phải khai báo ít nhất một issuer hợp lệ của Google")
        List<String> issuers
) {
    public GoogleOAuthProperties {
        issuers = issuers == null ? List.of() : List.copyOf(issuers);
    }

    public boolean configured() {
        return StringUtils.hasText(clientId);
    }
}
