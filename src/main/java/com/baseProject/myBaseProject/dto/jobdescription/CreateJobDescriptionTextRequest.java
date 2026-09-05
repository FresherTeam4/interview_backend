package com.baseProject.myBaseProject.dto.jobdescription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateJobDescriptionTextRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String text) {
}
