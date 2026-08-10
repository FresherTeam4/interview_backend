package com.baseProject.myBaseProject.dto.auth;

public record AuthResult(
        AuthResponse body,
        String refreshToken
) {}
