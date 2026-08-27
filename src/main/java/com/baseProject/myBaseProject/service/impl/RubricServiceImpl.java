package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.entity.Rubric;
import com.baseProject.myBaseProject.entity.RubricCriterion;
import com.baseProject.myBaseProject.entity.RubricCriterionLevel;
import com.baseProject.myBaseProject.entity.RubricVersion;
import com.baseProject.myBaseProject.exception.RubricNotAvailableException;
import com.baseProject.myBaseProject.repository.RubricRepository;
import com.baseProject.myBaseProject.service.RubricService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class RubricServiceImpl implements RubricService {

    private static final BigDecimal REQUIRED_TOTAL_WEIGHT = new BigDecimal("1.000");

    private final RubricRepository rubricRepository;

    @Override
    @Transactional(readOnly = true)
    public RubricVersion getCurrentPublishedVersion() {
        Rubric rubric = rubricRepository.findByCodeWithCurrentVersion(MVP_RUBRIC_CODE)
                .orElseThrow(() -> unavailable("missing current version"));
        RubricVersion version = rubric.getCurrentVersion();

        validatePublishedVersion(version);
        return version;
    }

    private void validatePublishedVersion(RubricVersion version) {
        if (version.getPublishedAt() == null) {
            throw unavailable("current version is not published");
        }
        if (version.getCriteria().isEmpty()) {
            throw unavailable("current version has no criteria");
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        Set<Short> displayOrders = new HashSet<>();
        for (RubricCriterion criterion : version.getCriteria()) {
            validateCriterion(criterion, displayOrders);
            totalWeight = totalWeight.add(criterion.getWeight());
        }

        if (totalWeight.compareTo(REQUIRED_TOTAL_WEIGHT) != 0) {
            throw unavailable("criterion weights do not total 1.000");
        }
    }

    private void validateCriterion(RubricCriterion criterion, Set<Short> displayOrders) {
        if (criterion.getWeight() == null
                || criterion.getWeight().signum() <= 0
                || criterion.getWeight().compareTo(BigDecimal.ONE) > 0) {
            throw unavailable("criterion has an invalid weight");
        }
        if (!displayOrders.add(criterion.getDisplayOrder())) {
            throw unavailable("criterion display order is duplicated");
        }

        short maxScore = criterion.getMaxScore();
        if (maxScore <= 0 || criterion.getLevels().size() != maxScore) {
            throw unavailable("criterion level count does not match max score");
        }

        Set<Short> levelNumbers = new HashSet<>();
        for (RubricCriterionLevel level : criterion.getLevels()) {
            validateLevel(level, maxScore, levelNumbers);
        }
        for (short expectedLevel = 1; expectedLevel <= maxScore; expectedLevel++) {
            if (!levelNumbers.contains(expectedLevel)) {
                throw unavailable("criterion level sequence is incomplete");
            }
        }
    }

    private void validateLevel(
            RubricCriterionLevel level,
            short maxScore,
            Set<Short> levelNumbers) {
        if (level.getLevelNo() <= 0
                || level.getLevelNo() > maxScore
                || !levelNumbers.add(level.getLevelNo())) {
            throw unavailable("criterion has an invalid level number");
        }
        if (level.getScoreValue() == null
                || level.getScoreValue().signum() < 0
                || level.getScoreValue().compareTo(BigDecimal.valueOf(maxScore)) > 0) {
            throw unavailable("criterion level has an invalid score");
        }
        if (level.getLabel().isBlank() || level.getDescriptor().isBlank()) {
            throw unavailable("criterion level has empty display content");
        }
    }

    private RubricNotAvailableException unavailable(String reason) {
        log.warn("Rubric {} is unavailable: {}", MVP_RUBRIC_CODE, reason);
        return new RubricNotAvailableException();
    }
}
