package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        @Positive
        long expirationMs,

        @NotBlank
        String cookieName,

        @NotBlank
        String cookiePath,

        boolean cookieSecure,

        @Pattern(regexp = "Strict|Lax|None", message = "cookie-same-site chỉ nhận Strict, Lax hoặc None")
        String cookieSameSite
) {
    @AssertTrue(message = "cookie-same-site=None bắt buộc phải đi kèm cookie-secure=true")
    public boolean isSameSiteNoneUsedWithSecure() {
        return !"None".equals(cookieSameSite) || cookieSecure;
    }
}
