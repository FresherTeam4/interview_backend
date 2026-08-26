package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionSummaryResponse;
import com.baseProject.myBaseProject.entity.JobDescription;

import org.springframework.stereotype.Component;

@Component
public class JobDescriptionMapper {

    public JobDescriptionResponse toResponse(JobDescription jobDescription) {
        return new JobDescriptionResponse(
                jobDescription.getId(),
                jobDescription.getTitle(),
                jobDescription.getSourceType(),
                jobDescription.getStatus(),
                jobDescription.getOriginalFilename(),
                jobDescription.getRawText(),
                jobDescription.getConfirmedText(),
                jobDescription.getConfirmedAt(),
                jobDescription.getCreatedAt(),
                jobDescription.getUpdatedAt());
    }

    public JobDescriptionSummaryResponse toSummary(JobDescription jobDescription) {
        return new JobDescriptionSummaryResponse(
                jobDescription.getId(),
                jobDescription.getTitle(),
                jobDescription.getSourceType(),
                jobDescription.getStatus(),
                jobDescription.getOriginalFilename(),
                jobDescription.getConfirmedAt(),
                jobDescription.getCreatedAt(),
                jobDescription.getUpdatedAt());
    }
}
