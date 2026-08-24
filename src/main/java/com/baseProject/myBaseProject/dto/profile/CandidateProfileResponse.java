package com.baseProject.myBaseProject.dto.profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.ProfileEducation;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.baseProject.myBaseProject.enums.ProfileSource;
import com.baseProject.myBaseProject.enums.SeniorityLevel;
import com.baseProject.myBaseProject.enums.SkillCategory;

/**
 * Hồ sơ ứng viên trả về cho FE. Cờ {@code userEdited} để FE đánh dấu dòng nào người dùng
 * đã tự sửa so với bản AI bóc tách.
 */
public record CandidateProfileResponse(
        Long id,
        Long cvDocumentId,
        String headline,
        BigDecimal yearsExperience,
        String targetPosition,
        SeniorityLevel seniorityLevel,
        ProfileSource source,
        Instant confirmedAt,
        Instant updatedAt,
        List<EducationResponse> educations,
        List<SkillResponse> skills,
        List<ProjectResponse> projects
) {
    public static CandidateProfileResponse of(CandidateProfile profile,
                                              List<ProfileEducation> educations,
                                              List<ProfileSkill> skills,
                                              List<ProfileProject> projects) {
        return new CandidateProfileResponse(
                profile.getId(),
                profile.getCvDocument().getId(),
                profile.getHeadline(),
                profile.getYearsExperience(),
                profile.getTargetPosition(),
                profile.getSeniorityLevel(),
                profile.getSource(),
                profile.getConfirmedAt(),
                profile.getUpdatedAt(),
                educations.stream().map(EducationResponse::from).toList(),
                skills.stream().map(SkillResponse::from).toList(),
                projects.stream().map(ProjectResponse::from).toList()
        );
    }

    public record EducationResponse(
            Long id,
            String school,
            String degree,
            String fieldOfStudy,
            Integer startYear,
            Integer endYear,
            boolean userEdited
    ) {
        static EducationResponse from(ProfileEducation education) {
            return new EducationResponse(
                    education.getId(),
                    education.getSchool(),
                    education.getDegree(),
                    education.getFieldOfStudy(),
                    education.getStartYear(),
                    education.getEndYear(),
                    education.isUserEdited()
            );
        }
    }

    public record SkillResponse(
            Long id,
            String name,
            SkillCategory category,
            boolean userEdited
    ) {
        static SkillResponse from(ProfileSkill skill) {
            return new SkillResponse(skill.getId(), skill.getName(), skill.getCategory(),
                    skill.isUserEdited());
        }
    }

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            String roleInProject,
            String techStack,
            LocalDate startDate,
            LocalDate endDate,
            boolean userEdited
    ) {
        static ProjectResponse from(ProfileProject project) {
            return new ProjectResponse(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    project.getRoleInProject(),
                    project.getTechStack(),
                    project.getStartDate(),
                    project.getEndDate(),
                    project.isUserEdited()
            );
        }
    }
}
