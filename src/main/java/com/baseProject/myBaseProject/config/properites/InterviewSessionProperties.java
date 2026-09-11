package com.baseProject.myBaseProject.config.properites;

import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.interview-session")
public record InterviewSessionProperties(
        @NotEmpty List<@Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") String> supportedLanguages,
        @NotEmpty List<@Positive Integer> supportedDurations,
        @NotEmpty List<@NotNull InterviewSessionMode> supportedModes) {

    public InterviewSessionProperties {
        supportedLanguages = supportedLanguages == null ? List.of() : List.copyOf(supportedLanguages);
        supportedDurations = supportedDurations == null ? List.of() : List.copyOf(supportedDurations);
        supportedModes = supportedModes == null ? List.of() : List.copyOf(supportedModes);
    }
}
