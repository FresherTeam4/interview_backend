package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import com.baseProject.myBaseProject.entity.InterviewTemplate;
import com.baseProject.myBaseProject.entity.JobDescriptionDocument;
import org.springframework.stereotype.Component;

@Component
public class JobDescriptionMapper {
    public JobDescriptionResponse toResponse(JobDescriptionDocument document,
                                             InterviewTemplate template) {
        return new JobDescriptionResponse(
                document.getId(), document.getSourceType(), document.getOriginalFilename(),
                document.getContentType(), document.getFileSizeBytes(), document.getStatus(),
                document.getErrorCode(), document.getFailureStage(), document.getStatusMessage(), document.getUploadedAt(),
                document.getProcessedAt(), template == null ? null : template.getId(),
                template != null && template.isConfirmed(),
                template == null ? null : template.getTitle());
    }
}
