package com.baseProject.myBaseProject.dto.admin;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull Boolean enabled) {
}
