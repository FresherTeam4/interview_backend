package com.baseProject.myBaseProject.service.impl;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.entity.TechStack;
import com.baseProject.myBaseProject.entity.Technology;
import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.importer.QuestionImportRowValidator;
import com.baseProject.myBaseProject.repository.QuestionImportRowRepository;
import com.baseProject.myBaseProject.repository.QuestionRepository;
import com.baseProject.myBaseProject.repository.TechStackRepository;
import com.baseProject.myBaseProject.repository.TechnologyRepository;
import com.baseProject.myBaseProject.service.QuestionImportRowProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionImportRowProcessorImpl implements QuestionImportRowProcessor {
    private final QuestionImportRowRepository rowRepository;
    private final QuestionRepository questionRepository;
    private final TechStackRepository techStackRepository;
    private final TechnologyRepository technologyRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long rowId) {
        QuestionImportRow row = findRow(rowId);
        if (row.getStatus() != QuestionImportRowStatus.VALID) {
            return;
        }
        Question existing = questionRepository
                .findByContentFingerprint(row.getContentFingerprint())
                .orElse(null);
        if (existing != null) {
            markDuplicate(row, existing);
            return;
        }

        Set<String> requestedStackCodes = QuestionImportRowValidator
                .parseCodes(row.getTechStackCodes());
        Set<String> requestedTechnologyCodes = QuestionImportRowValidator
                .parseCodes(row.getTechnologyCodes());
        Set<TechStack> techStacks = loadTechStacks(requestedStackCodes);
        Set<Technology> technologies = loadTechnologies(requestedTechnologyCodes);

        Question question = Question.builder()
                .contentVi(row.getContentVi())
                .contentEn(row.getContentEn())
                .contentFingerprint(row.getContentFingerprint())
                .techStacks(techStacks)
                .technologies(technologies)
                .level(QuestionLevel.valueOf(row.getLevelValue()))
                .questionType(QuestionType.valueOf(row.getQuestionTypeValue()))
                .difficulty(QuestionDifficulty.valueOf(row.getDifficultyValue()))
                .companyRef(row.getCompanyRef())
                .createdBy(row.getImportJob().getCreatedBy())
                .active(Boolean.parseBoolean(row.getActiveValue()))
                .build();
        Question saved = questionRepository.saveAndFlush(question);
        row.setCreatedQuestion(saved);
        row.setStatus(QuestionImportRowStatus.IMPORTED);
        row.setErrorCode(null);
        row.setErrorMessage(null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long rowId, Throwable failure) {
        QuestionImportRow row = findRow(rowId);
        if (row.getStatus() != QuestionImportRowStatus.VALID) {
            return;
        }
        Question existing = row.getContentFingerprint() == null
                ? null
                : questionRepository.findByContentFingerprint(row.getContentFingerprint())
                        .orElse(null);
        if (existing != null) {
            markDuplicate(row, existing);
            return;
        }
        row.setStatus(QuestionImportRowStatus.IMPORT_FAILED);
        row.setErrorCode("IMPORT_FAILED");
        row.setErrorMessage(truncate(failure.getMessage(), 1000));
    }

    private QuestionImportRow findRow(Long rowId) {
        return rowRepository.findById(rowId)
                .orElseThrow(() -> new ResourceNotFoundException("Question import row not found"));
    }

    private Set<TechStack> loadTechStacks(Set<String> codes) {
        if (codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<TechStack> found = techStackRepository.findAllByCodeInAndActiveTrue(codes);
        ensureAllFound(codes, found.stream().map(TechStack::getCode).collect(Collectors.toSet()));
        return found.stream()
                .sorted(Comparator.comparing(TechStack::getCode))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Technology> loadTechnologies(Set<String> codes) {
        if (codes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Technology> found = technologyRepository.findAllByCodeInAndActiveTrue(codes);
        ensureAllFound(codes, found.stream().map(Technology::getCode).collect(Collectors.toSet()));
        return found.stream()
                .sorted(Comparator.comparing(Technology::getCode))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void ensureAllFound(Set<String> requested, Set<String> found) {
        Set<String> missing = new LinkedHashSet<>(requested);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Taxonomy changed after validation: " + missing);
        }
    }

    private static void markDuplicate(QuestionImportRow row, Question existing) {
        row.setStatus(QuestionImportRowStatus.DUPLICATE_IN_DATABASE);
        row.setExistingQuestion(existing);
        row.setErrorCode("DUPLICATE_IN_DATABASE");
        row.setErrorMessage("Question already exists as question " + existing.getId());
    }

    private static String truncate(String value, int maxLength) {
        String safe = value == null ? "Unexpected row import failure" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
