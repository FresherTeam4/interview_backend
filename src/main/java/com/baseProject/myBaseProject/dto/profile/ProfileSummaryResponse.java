package com.baseProject.myBaseProject.dto.profile;

import com.baseProject.myBaseProject.enums.ProfileSource;

import java.time.Instant;

public record ProfileSummaryResponse(
        Long id,
        long version,
        String name,
        Long cvDocumentId,
        String cvOriginalFilename,
        String headline,
        String targetPosition,
        String seniorityLevel,
        ProfileSource source,
        Instant confirmedAt,
        int educationCount,
        int skillCount,
        int projectCount,
        Instant createdAt,
        Instant updatedAt
) {
}
