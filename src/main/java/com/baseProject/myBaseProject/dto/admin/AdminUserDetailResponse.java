package com.baseProject.myBaseProject.dto.admin;

import com.baseProject.myBaseProject.enums.UserRole;

import java.time.Instant;

public record AdminUserDetailResponse(
        Long id,
        String fullName,
        String email,
        String avatarUrl,
        UserRole role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Activity activity) {

    public record Activity(
            long activeCvCount,
            long activeJobDescriptionCount,
            long interviewSessionCount,
            long completedInterviewSessionCount) {
    }
}
