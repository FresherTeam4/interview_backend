package com.baseProject.myBaseProject.dto.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProfileProjectDto(
        @Positive(message = "Project id must be positive")
        Long id,

        @NotBlank(message = "Project name is required")
        @Size(max = 255, message = "Project name must not exceed 255 characters")
        String name,

        @Size(max = 5000, message = "Project description must not exceed 5000 characters")
        String description,

        @Size(max = 150, message = "Project role must not exceed 150 characters")
        String roleInProject,

        @Size(max = 500, message = "Technology stack must not exceed 500 characters")
        String techStack,

        LocalDate startDate,
        LocalDate endDate,
        Boolean userEdited,
        Short displayOrder
) {
    @JsonIgnore
    @AssertTrue(message = "End date must be greater than or equal to start date")
    public boolean isDateOrderValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
