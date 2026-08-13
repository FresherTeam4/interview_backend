package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;
import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.TechStack;

public final class QuestionMapper {
    private QuestionMapper() {
    }

    public static QuestionResponse toResponse(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getContentVi(),
                question.getContentEn(),
                toTechStackResponse(question.getTechStack()),
                question.getLevel(),
                question.getQuestionType(),
                question.getDifficulty(),
                question.getCompanyRef(),
                question.getCreatedBy() == null ? null : question.getCreatedBy().getId(),
                question.isActive(),
                question.getVersion(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    public static TechStackSummaryResponse toTechStackResponse(TechStack techStack) {
        if (techStack == null) {
            return null;
        }
        return new TechStackSummaryResponse(
                techStack.getId(),
                techStack.getCode(),
                techStack.getNameVi(),
                techStack.getNameEn(),
                techStack.isActive()
        );
    }
}
