package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.UserRole;

import java.time.Instant;

public record AdminUserSummaryResponse(
        Long id,
        String fullName,
        String email,
        UserRole role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
}
