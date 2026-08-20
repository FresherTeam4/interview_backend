package com.baseProject.myBaseProject.config;

import java.util.List;

import com.baseProject.myBaseProject.enums.QuestionImportStatus;
import com.baseProject.myBaseProject.service.QuestionImportAsyncWorker;
import com.baseProject.myBaseProject.service.QuestionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionImportRecovery {
    private final QuestionImportService importService;
    private final QuestionImportAsyncWorker asyncWorker;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedImports() {
        importService.findJobIdsByStatuses(List.of(
                        QuestionImportStatus.UPLOADED,
                        QuestionImportStatus.VALIDATING
                ))
                .forEach(asyncWorker::validateAsync);
        importService.findJobIdsByStatuses(List.of(QuestionImportStatus.IMPORTING))
                .forEach(asyncWorker::importAsync);
    }
}
