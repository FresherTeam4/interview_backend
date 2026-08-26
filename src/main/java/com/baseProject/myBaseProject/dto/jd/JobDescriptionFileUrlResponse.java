package com.baseProject.myBaseProject.dto.jd;

import java.time.Instant;

public record JobDescriptionFileUrlResponse(
        String url,
        Instant expiresAt
) {
}
