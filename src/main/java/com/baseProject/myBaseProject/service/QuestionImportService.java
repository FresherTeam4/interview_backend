package com.baseProject.myBaseProject.service;

import java.util.List;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.imports.QuestionImportRowResponse;
import com.baseProject.myBaseProject.dto.question.imports.QuestionImportSummaryResponse;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import com.baseProject.myBaseProject.enums.QuestionImportStatus;
import org.springframework.web.multipart.MultipartFile;

public interface QuestionImportService {
    QuestionImportSummaryResponse stage(MultipartFile file, Long creatorId);

    QuestionImportSummaryResponse getSummary(Long importId);

    PageResponse<QuestionImportRowResponse> getRows(
            Long importId,
            QuestionImportRowStatus status,
            int page,
            int size
    );

    QuestionImportSummaryResponse prepareCommit(Long importId);

    byte[] exportResult(Long importId);

    List<Long> findJobIdsByStatuses(List<QuestionImportStatus> statuses);
}
