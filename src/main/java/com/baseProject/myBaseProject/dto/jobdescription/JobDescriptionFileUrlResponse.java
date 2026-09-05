package com.baseProject.myBaseProject.dto.jobdescription;

import java.time.Instant;

public record JobDescriptionFileUrlResponse(String url, Instant expiresAt) {
}
