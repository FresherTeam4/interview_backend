package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.interview.InterviewRubricResponse;
import com.baseProject.myBaseProject.entity.RubricCriterion;
import com.baseProject.myBaseProject.entity.RubricCriterionLevel;
import com.baseProject.myBaseProject.entity.RubricVersion;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class RubricMapper {

    public InterviewRubricResponse toResponse(RubricVersion version) {
        List<InterviewRubricResponse.Criterion> criteria = version.getCriteria().stream()
                .sorted(Comparator.comparingInt(RubricCriterion::getDisplayOrder))
                .map(this::toCriterion)
                .toList();
        return new InterviewRubricResponse(
                version.getRubric().getCode(),
                version.getRubric().getName(),
                version.getRubric().getDescription(),
                version.getVersionNo(),
                version.getPublishedAt(),
                criteria);
    }

    private InterviewRubricResponse.Criterion toCriterion(RubricCriterion criterion) {
        List<InterviewRubricResponse.Level> levels = criterion.getLevels().stream()
                .sorted(Comparator.comparingInt(RubricCriterionLevel::getLevelNo))
                .map(level -> new InterviewRubricResponse.Level(
                        level.getLevelNo(),
                        level.getLabel(),
                        level.getDescriptor(),
                        level.getScoreValue()))
                .toList();
        return new InterviewRubricResponse.Criterion(
                criterion.getCode(),
                criterion.getName(),
                criterion.getDescription(),
                criterion.getWeight(),
                criterion.getMaxScore(),
                criterion.getDisplayOrder(),
                levels);
    }
}
