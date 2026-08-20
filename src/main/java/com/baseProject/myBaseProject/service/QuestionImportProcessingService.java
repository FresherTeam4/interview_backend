package com.baseProject.myBaseProject.service;

import java.util.List;

public interface QuestionImportProcessingService {
    void validateJob(Long importId);

    List<Long> findValidRowIds(Long importId);

    void finalizeImport(Long importId);

    void markJobFailed(Long importId, Throwable failure);
}
