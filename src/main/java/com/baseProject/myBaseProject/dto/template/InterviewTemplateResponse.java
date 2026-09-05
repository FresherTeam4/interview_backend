package com.baseProject.myBaseProject.dto.template;

import java.time.Instant;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

public record InterviewTemplateResponse(
        Long id,
        Long sourceJobDescriptionId,
        String title,
        String jobTitle,
        String targetSeniority,
        JobAnalysis content,
        boolean confirmed,
        Instant confirmedAt,
        boolean published,
        Instant publishedAt,
        Instant archivedAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
