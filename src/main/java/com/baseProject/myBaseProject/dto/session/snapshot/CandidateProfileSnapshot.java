package com.baseProject.myBaseProject.dto.session.snapshot;

import com.baseProject.myBaseProject.dto.profile.ProfileEducationDto;
import com.baseProject.myBaseProject.dto.profile.ProfileProjectDto;
import com.baseProject.myBaseProject.dto.profile.ProfileSkillDto;

import java.math.BigDecimal;
import java.util.List;

public record CandidateProfileSnapshot(
        String snapshotSchemaVersion,
        Long profileId,
        long profileVersion,
        String name,
        String headline,
        String summary,
        BigDecimal yearsExperience,
        String targetPosition,
        String seniorityLevel,
        List<ProfileEducationDto> educations,
        List<ProfileSkillDto> skills,
        List<ProfileProjectDto> projects) {
}
