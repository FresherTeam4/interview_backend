package com.baseProject.myBaseProject.dto.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.baseProject.myBaseProject.enums.SeniorityLevel;
import com.baseProject.myBaseProject.enums.SkillCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Người dùng gửi lên toàn bộ hồ sơ sau khi sửa (US-3). Service ghi đè các danh sách con
 * bằng đúng những gì nhận được, nên FE phải gửi đủ danh sách chứ không gửi từng phần.
 */
public record UpdateCandidateProfileRequest(
        @Size(max = 255)
        String headline,

        @DecimalMin(value = "0.0", message = "Số năm kinh nghiệm không được âm")
        @DecimalMax(value = "99.9", message = "Số năm kinh nghiệm tối đa là 99.9")
        BigDecimal yearsExperience,

        @Size(max = 150)
        String targetPosition,

        SeniorityLevel seniorityLevel,

        @Valid
        @Size(max = 50, message = "Tối đa 50 mục học vấn")
        List<EducationRequest> educations,

        @Valid
        @Size(max = 200, message = "Tối đa 200 kỹ năng")
        List<SkillRequest> skills,

        @Valid
        @Size(max = 50, message = "Tối đa 50 dự án")
        List<ProjectRequest> projects
) {
    public List<EducationRequest> educationList() {
        return educations == null ? List.of() : educations;
    }

    public List<SkillRequest> skillList() {
        return skills == null ? List.of() : skills;
    }

    public List<ProjectRequest> projectList() {
        return projects == null ? List.of() : projects;
    }

    public record EducationRequest(
            @NotBlank(message = "Tên trường không được để trống")
            @Size(max = 255)
            String school,

            @Size(max = 150)
            String degree,

            @Size(max = 150)
            String fieldOfStudy,

            @Min(1950) @Max(2100)
            Integer startYear,

            @Min(1950) @Max(2100)
            Integer endYear
    ) { }

    public record SkillRequest(
            @NotBlank(message = "Tên kỹ năng không được để trống")
            @Size(max = 80)
            String name,

            SkillCategory category
    ) { }

    public record ProjectRequest(
            @NotBlank(message = "Tên dự án không được để trống")
            @Size(max = 255)
            String name,

            @Size(max = 5000)
            String description,

            @Size(max = 150)
            String roleInProject,

            @Size(max = 1000)
            String techStack,

            LocalDate startDate,

            LocalDate endDate
    ) { }
}
