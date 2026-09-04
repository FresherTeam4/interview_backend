package com.baseProject.myBaseProject.config.properites;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @DefaultValue("true")
        boolean enabled,

        @DefaultValue("gemini-2.0-flash")
        String defaultModel,

        @DefaultValue("0.1")
        Double temperature
) {
}
