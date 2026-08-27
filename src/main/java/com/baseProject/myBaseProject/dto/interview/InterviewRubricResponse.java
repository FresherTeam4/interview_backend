package com.baseProject.myBaseProject.dto.interview;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InterviewRubricResponse(
        String code,
        String name,
        String description,
        int version,
        Instant publishedAt,
        List<Criterion> criteria) {

    public record Criterion(
            String code,
            String name,
            String description,
            BigDecimal weight,
            short maxScore,
            short displayOrder,
            List<Level> levels) {
    }

    public record Level(
            short levelNo,
            String label,
            String descriptor,
            BigDecimal scoreValue) {
    }
}
