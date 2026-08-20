package com.baseProject.myBaseProject.dto.question.imports;

import java.time.Instant;

import com.baseProject.myBaseProject.entity.QuestionImportJob;
import com.baseProject.myBaseProject.enums.QuestionImportStatus;

public record QuestionImportSummaryResponse(
        Long id,
        String fileName,
        QuestionImportStatus status,
        int totalRows,
        int validRows,
        int invalidRows,
        int duplicateRows,
        int importedRows,
        int failedRows,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static QuestionImportSummaryResponse from(QuestionImportJob job) {
        return new QuestionImportSummaryResponse(
                job.getId(),
                job.getOriginalFileName(),
                job.getStatus(),
                job.getTotalRows(),
                job.getValidRows(),
                job.getInvalidRows(),
                job.getDuplicateRows(),
                job.getImportedRows(),
                job.getFailedRows(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt()
        );
    }
}
