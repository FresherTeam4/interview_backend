package com.baseProject.myBaseProject.jobdescription.impl;

import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.JobDescriptionProcessingRecoveryService;
import com.baseProject.myBaseProject.repository.JobDescriptionDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDescriptionProcessingRecoveryServiceImpl implements JobDescriptionProcessingRecoveryService {
    private final JobDescriptionDocumentRepository documents;

    @Override
    @Transactional
    public int failInterruptedJobs(Instant applicationStartedAt) {
        List<JobDescriptionDocument> interrupted = documents.findByStatusInAndUploadedAtBefore(
                List.of(JobDescriptionStatus.UPLOADED, JobDescriptionStatus.EXTRACTING,
                        JobDescriptionStatus.ANALYZING), applicationStartedAt);
        interrupted.forEach(document -> document.markFailed(
                ErrorCode.JD_PROCESSING_FAILED.name(),
                "Job description processing was interrupted by a server restart; please retry"));
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted job description jobs as FAILED", interrupted.size());
        }
        return interrupted.size();
    }
}
