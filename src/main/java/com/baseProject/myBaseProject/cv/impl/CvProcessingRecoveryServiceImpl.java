package com.baseProject.myBaseProject.cv.impl;

import com.baseProject.myBaseProject.cv.CvProcessingRecoveryService;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;
import com.baseProject.myBaseProject.repository.CvDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvProcessingRecoveryServiceImpl implements CvProcessingRecoveryService {

    private static final String INTERRUPTED_MESSAGE =
            "CV processing was interrupted by a server restart; please retry";

    private final CvDocumentRepository cvDocumentRepository;

    @Override
    @Transactional
    public int failInterruptedJobs(Instant applicationStartedAt) {
        // Lọc tại database để không tải mọi CV đang xử lý vào bộ nhớ rồi mới so thời gian.
        List<CvDocument> interrupted = cvDocumentRepository.findByStatusInAndUploadedAtBefore(
                List.of(CvDocumentStatus.UPLOADED, CvDocumentStatus.PARSING),
                applicationStartedAt);

        interrupted.forEach(document -> document.markFailed(INTERRUPTED_MESSAGE));

        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted CV processing jobs as FAILED", interrupted.size());
        }
        return interrupted.size();
    }
}
