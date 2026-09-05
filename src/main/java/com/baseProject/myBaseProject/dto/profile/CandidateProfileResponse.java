package com.baseProject.myBaseProject.dto.profile;

import com.baseProject.myBaseProject.enums.ProfileSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CandidateProfileResponse(
        Long id,
        long version,
        String name,
        Long cvDocumentId,
        String cvOriginalFilename,
        String headline,
        String summary,
        BigDecimal yearsExperience,
        String targetPosition,
        String seniorityLevel,
        ProfileSource source,
        Instant confirmedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ProfileEducationDto> educations,
        List<ProfileSkillDto> skills,
        List<ProfileProjectDto> projects
) {
}
