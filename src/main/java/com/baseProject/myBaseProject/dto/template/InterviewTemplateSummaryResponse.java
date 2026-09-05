package com.baseProject.myBaseProject.dto.template;

import java.time.Instant;

public record InterviewTemplateSummaryResponse(
        Long id,
        Long sourceJobDescriptionId,
        String title,
        String jobTitle,
        String targetSeniority,
        boolean confirmed,
        boolean published,
        Instant archivedAt,
        Instant updatedAt) {
}
