package com.baseProject.myBaseProject.dto.jobdescription;

import java.time.Instant;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;

public record JobDescriptionAnalysisResponse(
        Long jobDescriptionId,
        String extractedText,
        JobAnalysis analysis,
        String schemaVersion,
        String modelName,
        Integer durationMs,
        Integer tokenCount,
        Instant createdAt) {
}
