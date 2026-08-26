package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.jd.CreateTextJobDescriptionRequest;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionSummaryResponse;
import com.baseProject.myBaseProject.dto.jd.UpdateJobDescriptionRequest;

import org.springframework.web.multipart.MultipartFile;

public interface JobDescriptionService {

    JobDescriptionResponse createText(Long userId, CreateTextJobDescriptionRequest request);

    JobDescriptionResponse createFile(Long userId, String title, MultipartFile file);

    PageResponse<JobDescriptionSummaryResponse> list(Long userId, int page, int size);

    JobDescriptionResponse get(Long userId, Long jobDescriptionId);

    JobDescriptionFileUrlResponse fileUrl(Long userId, Long jobDescriptionId);

    JobDescriptionResponse update(
            Long userId,
            Long jobDescriptionId,
            UpdateJobDescriptionRequest request);

    JobDescriptionResponse confirm(Long userId, Long jobDescriptionId);

    void delete(Long userId, Long jobDescriptionId);
}
