package com.baseProject.myBaseProject.dto.session;

import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateInterviewSessionRequest(
        @NotNull(message = "Template id is required")
        @Positive(message = "Template id must be positive")
        Long templateId,

        @NotNull(message = "Profile id is required")
        @Positive(message = "Profile id must be positive")
        Long profileId,

        @NotBlank(message = "Language code is required")
        @Size(max = 10, message = "Language code must not exceed 10 characters")
        String languageCode,

        @NotNull(message = "Duration is required")
        @Positive(message = "Duration must be positive")
        Integer durationMinutes,

        @NotNull(message = "Interviewer style is required")
        InterviewerStyle interviewerStyle,

        InterviewSessionMode mode) {
}
