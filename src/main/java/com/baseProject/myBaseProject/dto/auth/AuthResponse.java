package com.baseProject.myBaseProject.dto.auth;

import com.baseProject.myBaseProject.enums.UserRole;

public record AuthResponse(
    String accessToken,
    Long userId,
    String email,
    UserRole role
) {}
