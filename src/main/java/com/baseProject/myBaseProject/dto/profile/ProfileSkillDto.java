package com.baseProject.myBaseProject.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProfileSkillDto(
        @Positive(message = "Skill id must be positive")
        Long id,

        @NotBlank(message = "Skill name is required")
        @Size(max = 80, message = "Skill name must not exceed 80 characters")
        String name,

        @Size(max = 50, message = "Skill category must not exceed 50 characters")
        String category,

        Boolean userEdited,
        Short displayOrder
) {
}
