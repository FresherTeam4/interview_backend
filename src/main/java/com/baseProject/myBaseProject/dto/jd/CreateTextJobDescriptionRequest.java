package com.baseProject.myBaseProject.dto.jd;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTextJobDescriptionRequest(
        @NotBlank(message = "Tiêu đề JD không được để trống")
        @Size(max = 200, message = "Tiêu đề JD tối đa 200 ký tự")
        String title,

        String text
) {
}
