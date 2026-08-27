package com.baseProject.myBaseProject.dto.interview;

import com.baseProject.myBaseProject.enums.InterviewDifficulty;
import com.baseProject.myBaseProject.enums.SessionMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInterviewSessionRequest(
        @NotNull(message = "profileId là bắt buộc")
        @Positive(message = "profileId phải là số dương")
        Long profileId,

        @NotNull(message = "jobDescriptionId là bắt buộc")
        @Positive(message = "jobDescriptionId phải là số dương")
        Long jobDescriptionId,

        @NotNull(message = "difficulty là bắt buộc")
        InterviewDifficulty difficulty,

        @NotNull(message = "mode là bắt buộc")
        SessionMode mode,

        @NotBlank(message = "languageCode là bắt buộc")
        @Size(max = 10, message = "languageCode tối đa 10 ký tự")
        @Pattern(regexp = "vi", message = "MVP hiện chỉ hỗ trợ languageCode vi")
        String languageCode) {
}
