package com.baseProject.myBaseProject.service;

import java.util.List;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionFilter;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.QuestionUpdateRequest;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;
import com.baseProject.myBaseProject.dto.question.TechnologySummaryResponse;
import com.baseProject.myBaseProject.enums.TechnologyType;

public interface QuestionService {
    QuestionResponse create(QuestionCreateRequest request, Long creatorId);

    QuestionResponse getById(Long id);

    PageResponse<QuestionResponse> getAll(int page, int size);

    PageResponse<QuestionResponse> search(QuestionFilter filter, int page, int size);

    QuestionResponse update(Long id, QuestionUpdateRequest request);

    void delete(Long id);

    List<TechStackSummaryResponse> getTechStacks(boolean activeOnly);

    List<TechnologySummaryResponse> getTechnologies(boolean activeOnly, TechnologyType type);
}
