package com.baseProject.myBaseProject.service;

import java.util.List;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.QuestionUpdateRequest;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;

public interface QuestionService {
    QuestionResponse create(QuestionCreateRequest request, Long creatorId);

    QuestionResponse getById(Long id);

    PageResponse<QuestionResponse> getAll(int page, int size);

    QuestionResponse update(Long id, QuestionUpdateRequest request);

    void delete(Long id);

    List<TechStackSummaryResponse> getTechStacks(boolean activeOnly);
}
