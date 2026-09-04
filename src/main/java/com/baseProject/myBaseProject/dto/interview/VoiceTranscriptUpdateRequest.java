package com.baseProject.myBaseProject.dto.interview;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record VoiceTranscriptUpdateRequest(
        @NotBlank(message = "editedText là bắt buộc")
        @Size(max = 10000, message = "editedText tối đa 10000 ký tự")
        String editedText,

        @NotNull(message = "expectedAttemptVersion là bắt buộc")
        @PositiveOrZero(message = "expectedAttemptVersion không được âm")
        Long expectedAttemptVersion) {
}
