package com.baseProject.myBaseProject.dto.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProfileEducationDto(
        @Positive(message = "Education id must be positive")
        Long id,

        @NotBlank(message = "School is required")
        @Size(max = 255, message = "School must not exceed 255 characters")
        String school,

        @Size(max = 150, message = "Degree must not exceed 150 characters")
        String degree,

        @Size(max = 150, message = "Field of study must not exceed 150 characters")
        String fieldOfStudy,

        @Min(value = 1900, message = "Start year must be between 1900 and 2100")
        @Max(value = 2100, message = "Start year must be between 1900 and 2100")
        Short startYear,

        @Min(value = 1900, message = "End year must be between 1900 and 2100")
        @Max(value = 2100, message = "End year must be between 1900 and 2100")
        Short endYear,

        Boolean userEdited,
        Short displayOrder
) {
    @JsonIgnore
    @AssertTrue(message = "End year must be greater than or equal to start year")
    public boolean isYearOrderValid() {
        return startYear == null || endYear == null || endYear >= startYear;
    }
}
