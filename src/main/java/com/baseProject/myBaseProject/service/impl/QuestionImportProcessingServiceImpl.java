package com.baseProject.myBaseProject.service.impl;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baseProject.myBaseProject.entity.Question;
import com.baseProject.myBaseProject.entity.QuestionImportJob;
import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.entity.TechStack;
import com.baseProject.myBaseProject.entity.Technology;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import com.baseProject.myBaseProject.enums.QuestionImportStatus;
import com.baseProject.myBaseProject.exception.QuestionImportStateException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.importer.QuestionImportRowValidator;
import com.baseProject.myBaseProject.importer.QuestionImportValidationResult;
import com.baseProject.myBaseProject.repository.QuestionImportJobRepository;
import com.baseProject.myBaseProject.repository.QuestionImportRowRepository;
import com.baseProject.myBaseProject.repository.QuestionRepository;
import com.baseProject.myBaseProject.repository.TechStackRepository;
import com.baseProject.myBaseProject.repository.TechnologyRepository;
import com.baseProject.myBaseProject.service.QuestionImportProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionImportProcessingServiceImpl implements QuestionImportProcessingService {
    private final QuestionImportJobRepository jobRepository;
    private final QuestionImportRowRepository rowRepository;
    private final QuestionRepository questionRepository;
    private final TechStackRepository techStackRepository;
    private final TechnologyRepository technologyRepository;
    private final QuestionImportRowValidator validator;
    private final Clock clock;

    @Override
    @Transactional
    public void validateJob(Long importId) {
        QuestionImportJob job = lockJob(importId);
        if (job.getStatus() != QuestionImportStatus.UPLOADED
                && job.getStatus() != QuestionImportStatus.VALIDATING) {
            return;
        }
        job.setStatus(QuestionImportStatus.VALIDATING);
        if (job.getStartedAt() == null) {
            job.setStartedAt(clock.instant());
        }
        job.setErrorMessage(null);

        Map<String, TechStack> techStacks = techStackRepository
                .findAllByActiveTrueOrderByNameEnAsc()
                .stream()
                .collect(Collectors.toMap(
                        TechStack::getCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Technology> technologies = technologyRepository
                .findAllByActiveTrueOrderByTechnologyTypeAscNameEnAsc()
                .stream()
                .collect(Collectors.toMap(
                        Technology::getCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<QuestionImportRow> rows = rowRepository
                .findAllByImportJobIdOrderByRowNumberAsc(importId);
        Map<String, Integer> firstRowByFingerprint = new LinkedHashMap<>();
        Map<String, QuestionImportRow> validByFingerprint = new LinkedHashMap<>();

        for (QuestionImportRow row : rows) {
            resetValidationResult(row);
            QuestionImportValidationResult result = validator.validate(row, techStacks, technologies);
            if (!result.valid()) {
                markInvalid(row, result.errorCode(), result.errorMessage());
                continue;
            }
            applyNormalizedValues(row, result);
            Integer firstRow = firstRowByFingerprint.putIfAbsent(
                    result.fingerprint(),
                    row.getRowNumber()
            );
            if (firstRow != null) {
                row.setStatus(QuestionImportRowStatus.DUPLICATE_IN_FILE);
                row.setErrorCode("DUPLICATE_IN_FILE");
                row.setErrorMessage("Duplicate of CSV row " + firstRow);
                continue;
            }
            row.setStatus(QuestionImportRowStatus.VALID);
            validByFingerprint.put(result.fingerprint(), row);
        }

        if (!validByFingerprint.isEmpty()) {
            List<Question> existing = questionRepository.findAllByContentFingerprintIn(
                    validByFingerprint.keySet()
            );
            for (Question question : existing) {
                QuestionImportRow row = validByFingerprint.get(question.getContentFingerprint());
                if (row != null) {
                    row.setStatus(QuestionImportRowStatus.DUPLICATE_IN_DATABASE);
                    row.setErrorCode("DUPLICATE_IN_DATABASE");
                    row.setErrorMessage(
                            "Question already exists as question " + question.getId()
                    );
                    row.setExistingQuestion(question);
                }
            }
        }

        rowRepository.saveAll(rows);
        updatePreviewCounts(job, rows);
        job.setStatus(QuestionImportStatus.READY_TO_IMPORT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findValidRowIds(Long importId) {
        QuestionImportJob job = jobRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Question import job not found"));
        if (job.getStatus() != QuestionImportStatus.IMPORTING) {
            throw new QuestionImportStateException("Import job is not in IMPORTING state");
        }
        return rowRepository.findIdsByJobIdAndStatus(
                importId,
                QuestionImportRowStatus.VALID
        );
    }

    @Override
    @Transactional
    public void finalizeImport(Long importId) {
        QuestionImportJob job = lockJob(importId);
        if (job.getStatus() != QuestionImportStatus.IMPORTING) {
            return;
        }
        int imported = count(importId, QuestionImportRowStatus.IMPORTED);
        int failed = count(importId, QuestionImportRowStatus.IMPORT_FAILED);
        int duplicates = count(importId, QuestionImportRowStatus.DUPLICATE_IN_FILE)
                + count(importId, QuestionImportRowStatus.DUPLICATE_IN_DATABASE);
        int remaining = count(importId, QuestionImportRowStatus.VALID);
        job.setImportedRows(imported);
        job.setFailedRows(failed);
        job.setDuplicateRows(duplicates);
        if (remaining > 0) {
            throw new QuestionImportStateException(
                    "Import job still has %d unprocessed rows".formatted(remaining)
            );
        }
        job.setStatus(failed == 0
                ? QuestionImportStatus.COMPLETED
                : QuestionImportStatus.COMPLETED_WITH_ERRORS);
        job.setCompletedAt(clock.instant());
    }

    @Override
    @Transactional
    public void markJobFailed(Long importId, Throwable failure) {
        jobRepository.findByIdForUpdate(importId).ifPresent(job -> {
            job.setStatus(QuestionImportStatus.FAILED);
            job.setErrorMessage(truncate(failure.getMessage(), 1000));
            job.setCompletedAt(clock.instant());
        });
    }

    private QuestionImportJob lockJob(Long importId) {
        return jobRepository.findByIdForUpdate(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Question import job not found"));
    }

    private static void resetValidationResult(QuestionImportRow row) {
        row.setStatus(QuestionImportRowStatus.PENDING);
        row.setErrorCode(null);
        row.setErrorMessage(null);
        row.setExistingQuestion(null);
        row.setCreatedQuestion(null);
        row.setContentFingerprint(null);
    }

    private static void applyNormalizedValues(
            QuestionImportRow row,
            QuestionImportValidationResult result
    ) {
        row.setContentVi(result.contentVi());
        row.setContentEn(result.contentEn());
        row.setLevelValue(result.level().name());
        row.setQuestionTypeValue(result.questionType().name());
        row.setDifficultyValue(result.difficulty().name());
        row.setCompanyRef(result.companyRef());
        row.setTechStackCodes(String.join("|", result.techStackCodes()));
        row.setTechnologyCodes(String.join("|", result.technologyCodes()));
        row.setActiveValue(Boolean.toString(result.active()));
        row.setContentFingerprint(result.fingerprint());
    }

    private static void markInvalid(QuestionImportRow row, String code, String message) {
        row.setStatus(QuestionImportRowStatus.INVALID);
        row.setErrorCode(code);
        row.setErrorMessage(truncate(message, 1000));
    }

    private static void updatePreviewCounts(
            QuestionImportJob job,
            Collection<QuestionImportRow> rows
    ) {
        job.setValidRows(count(rows, QuestionImportRowStatus.VALID));
        job.setInvalidRows(count(rows, QuestionImportRowStatus.INVALID));
        job.setDuplicateRows(
                count(rows, QuestionImportRowStatus.DUPLICATE_IN_FILE)
                        + count(rows, QuestionImportRowStatus.DUPLICATE_IN_DATABASE)
        );
        job.setImportedRows(0);
        job.setFailedRows(0);
    }

    private int count(Long jobId, QuestionImportRowStatus status) {
        return Math.toIntExact(rowRepository.countByImportJobIdAndStatus(jobId, status));
    }

    private static int count(
            Collection<QuestionImportRow> rows,
            QuestionImportRowStatus status
    ) {
        return (int) rows.stream().filter(row -> row.getStatus() == status).count();
    }

    private static String truncate(String value, int maxLength) {
        String safe = value == null ? "Unexpected import failure" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
