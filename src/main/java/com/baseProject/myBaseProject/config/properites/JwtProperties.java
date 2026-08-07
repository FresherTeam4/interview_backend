package com.baseProject.myBaseProject.config.properites;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    @NotBlank(message = "Thiếu biến môi trường JWT_SECRET")
    @Size(min = 32, message = "JWT secret phải dài tối thiểu 32 ký tự (256 bit) cho thuật toán HS256")
    String secret,

    @Positive
    long expirationMs
){}
