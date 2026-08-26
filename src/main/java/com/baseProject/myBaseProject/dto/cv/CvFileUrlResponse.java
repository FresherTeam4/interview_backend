package com.baseProject.myBaseProject.dto.cv;

import java.time.Instant;

public record CvFileUrlResponse(
        String url,
        Instant expiresAt
) {
}
