package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jd")
public record JobDescriptionProperties(
        @Positive(message = "app.jd.min-text-chars phải là số dương")
        int minTextChars,

        @Positive(message = "app.jd.max-text-chars phải là số dương")
        int maxTextChars,

        @Positive(message = "app.jd.max-per-user phải là số dương")
        int maxPerUser,

        @Positive(message = "app.jd.max-file-size-bytes phải là số dương")
        long maxFileSizeBytes,

        @Positive(message = "app.jd.max-pages phải là số dương")
        int maxPages
) {
    @AssertTrue(message = "app.jd.max-text-chars phải lớn hơn hoặc bằng min-text-chars")
    public boolean isTextRangeValid() {
        return maxTextChars >= minTextChars;
    }
}
