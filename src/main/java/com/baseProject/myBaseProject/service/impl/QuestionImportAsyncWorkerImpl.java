package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.service.QuestionImportAsyncWorker;
import com.baseProject.myBaseProject.service.QuestionImportProcessingService;
import com.baseProject.myBaseProject.service.QuestionImportRowProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionImportAsyncWorkerImpl implements QuestionImportAsyncWorker {
    private final QuestionImportProcessingService processingService;
    private final QuestionImportRowProcessor rowProcessor;

    @Override
    @Async("questionImportExecutor")
    public void validateAsync(Long importId) {
        try {
            processingService.validateJob(importId);
        } catch (Exception ex) {
            log.error("Question import validation job {} failed", importId, ex);
            processingService.markJobFailed(importId, ex);
        }
    }

    @Override
    @Async("questionImportExecutor")
    public void importAsync(Long importId) {
        try {
            for (Long rowId : processingService.findValidRowIds(importId)) {
                try {
                    rowProcessor.process(rowId);
                } catch (Exception ex) {
                    log.warn("Question import row {} failed", rowId, ex);
                    rowProcessor.recordFailure(rowId, ex);
                }
            }
            processingService.finalizeImport(importId);
        } catch (Exception ex) {
            log.error("Question import job {} failed", importId, ex);
            processingService.markJobFailed(importId, ex);
        }
    }
}
