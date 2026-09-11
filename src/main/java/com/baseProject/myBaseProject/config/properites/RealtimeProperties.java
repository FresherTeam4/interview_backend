package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        boolean enabled,
        @NotBlank String provider) {
}
