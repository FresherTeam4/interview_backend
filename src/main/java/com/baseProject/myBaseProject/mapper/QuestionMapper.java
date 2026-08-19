package com.baseProject.myBaseProject.mapper;

import java.util.Comparator;
import java.util.List;

import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;
import com.baseProject.myBaseProject.dto.question.TechnologySummaryResponse;
import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.TechStack;
import com.baseProject.myBaseProject.entity.Technology;

public final class QuestionMapper {
    private QuestionMapper() {
    }

    public static QuestionResponse toResponse(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getContentVi(),
                question.getContentEn(),
                toTechStackResponses(question.getTechStacks()),
                toTechnologyResponses(question.getTechnologies()),
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

    private static List<TechStackSummaryResponse> toTechStackResponses(
            Iterable<TechStack> techStacks
    ) {
        if (techStacks == null) {
            return List.of();
        }
        List<TechStack> sorted = new java.util.ArrayList<>();
        techStacks.forEach(sorted::add);
        return sorted.stream()
                .sorted(Comparator.comparing(TechStack::getCode))
                .map(QuestionMapper::toTechStackResponse)
                .toList();
    }

    private static List<TechnologySummaryResponse> toTechnologyResponses(
            Iterable<Technology> technologies
    ) {
        if (technologies == null) {
            return List.of();
        }
        List<Technology> sorted = new java.util.ArrayList<>();
        technologies.forEach(sorted::add);
        return sorted.stream()
                .sorted(Comparator.comparing(Technology::getCode))
                .map(QuestionMapper::toTechnologyResponse)
                .toList();
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

    public static TechnologySummaryResponse toTechnologyResponse(Technology technology) {
        if (technology == null) {
            return null;
        }
        return new TechnologySummaryResponse(
                technology.getId(),
                technology.getCode(),
                technology.getNameVi(),
                technology.getNameEn(),
                technology.getTechnologyType(),
                technology.isActive()
        );
    }
}
