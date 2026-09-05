package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.template.ConfirmInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.PublishInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.TemplatePageResponse;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;

public interface InterviewTemplateService {
    InterviewTemplateResponse get(Long userId, Long id);

    TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            Long userId, String scope, int page, int size);

    InterviewTemplateResponse update(Long userId, Long id, UpdateInterviewTemplateRequest request);

    InterviewTemplateResponse confirm(Long userId, Long id, ConfirmInterviewTemplateRequest request);

    InterviewTemplateResponse publish(Long userId, Long id, PublishInterviewTemplateRequest request);

    InterviewTemplateResponse unpublish(Long userId, Long id, long expectedVersion);

    InterviewTemplateResponse archive(Long userId, Long id, long expectedVersion);
}
