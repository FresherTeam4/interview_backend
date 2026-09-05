package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.jobdescription.CreateJobDescriptionTextRequest;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionAnalysisResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface JobDescriptionService {
    UploadResult upload(Long ownerId, MultipartFile file);

    UploadResult createFromText(Long ownerId, CreateJobDescriptionTextRequest request);

    List<JobDescriptionResponse> list(Long ownerId);

    JobDescriptionResponse get(Long ownerId, Long id);

    JobDescriptionAnalysisResponse analysis(Long ownerId, Long id);

    JobDescriptionFileUrlResponse fileUrl(Long ownerId, Long id);

    JobDescriptionResponse retry(Long ownerId, Long id);

    void delete(Long ownerId, Long id);

    record UploadResult(JobDescriptionResponse document, boolean reusedExisting) {
    }
}
