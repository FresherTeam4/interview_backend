package com.baseProject.myBaseProject.service.impl;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.baseProject.myBaseProject.constant.PaginationConstant;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.imports.QuestionImportRowResponse;
import com.baseProject.myBaseProject.dto.question.imports.QuestionImportSummaryResponse;
import com.baseProject.myBaseProject.entity.QuestionImportJob;
import com.baseProject.myBaseProject.entity.QuestionImportRow;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import com.baseProject.myBaseProject.enums.QuestionImportStatus;
import com.baseProject.myBaseProject.exception.QuestionImportException;
import com.baseProject.myBaseProject.exception.QuestionImportStateException;
import com.baseProject.myBaseProject.exception.ResourceNotFoundException;
import com.baseProject.myBaseProject.importer.CsvEscaper;
import com.baseProject.myBaseProject.importer.QuestionCsvFormatException;
import com.baseProject.myBaseProject.importer.QuestionCsvParser;
import com.baseProject.myBaseProject.importer.QuestionCsvRecord;
import com.baseProject.myBaseProject.repository.QuestionImportJobRepository;
import com.baseProject.myBaseProject.repository.QuestionImportRowRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.service.QuestionImportService;
import com.baseProject.myBaseProject.util.QuestionFingerprint;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionImportServiceImpl implements QuestionImportService {
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final Sort ROW_SORT = Sort.by(Sort.Order.asc("rowNumber"));

    private final QuestionImportJobRepository jobRepository;
    private final QuestionImportRowRepository rowRepository;
    private final UserAccountRepository userAccountRepository;
    private final QuestionCsvParser csvParser;

    @Override
    @Transactional
    public QuestionImportSummaryResponse stage(MultipartFile file, Long creatorId) {
        byte[] content = readAndValidateFile(file);
        List<QuestionCsvRecord> records;
        try {
            records = csvParser.parse(new ByteArrayInputStream(content));
        } catch (QuestionCsvFormatException ex) {
            throw new QuestionImportException(ex.getMessage());
        }
        UserAccount creator = userAccountRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator account not found"));

        QuestionImportJob job = QuestionImportJob.builder()
                .originalFileName(safeFileName(file.getOriginalFilename()))
                .fileSha256(QuestionFingerprint.sha256Bytes(content))
                .status(QuestionImportStatus.UPLOADED)
                .totalRows(records.size())
                .createdBy(creator)
                .build();
        jobRepository.saveAndFlush(job);

        List<QuestionImportRow> rows = records.stream()
                .map(record -> toEntity(job, record))
                .toList();
        rowRepository.saveAll(rows);
        return QuestionImportSummaryResponse.from(job);
    }

    @Override
    public QuestionImportSummaryResponse getSummary(Long importId) {
        return QuestionImportSummaryResponse.from(findJob(importId));
    }

    @Override
    public PageResponse<QuestionImportRowResponse> getRows(
            Long importId,
            QuestionImportRowStatus status,
            int page,
            int size
    ) {
        validatePage(page, size);
        findJob(importId);
        Page<QuestionImportRow> rows = status == null
                ? rowRepository.findAllByImportJobId(
                        importId,
                        PageRequest.of(page, size, ROW_SORT)
                )
                : rowRepository.findAllByImportJobIdAndStatus(
                        importId,
                        status,
                        PageRequest.of(page, size, ROW_SORT)
                );
        return PageResponse.from(rows.map(QuestionImportRowResponse::from));
    }

    @Override
    @Transactional
    public QuestionImportSummaryResponse prepareCommit(Long importId) {
        QuestionImportJob job = jobRepository.findByIdForUpdate(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Question import job not found"));
        if (job.getStatus() != QuestionImportStatus.READY_TO_IMPORT) {
            throw new QuestionImportStateException(
                    "Import job must be READY_TO_IMPORT before commit"
            );
        }
        if (job.getValidRows() < 1) {
            throw new QuestionImportStateException("Import job has no valid rows to import");
        }
        job.setStatus(QuestionImportStatus.IMPORTING);
        job.setErrorMessage(null);
        return QuestionImportSummaryResponse.from(job);
    }

    @Override
    public byte[] exportResult(Long importId) {
        findJob(importId);
        StringBuilder csv = new StringBuilder();
        csv.append("row_number,content_vi,status,error_code,error_message,")
                .append("existing_question_id,created_question_id\r\n");
        for (QuestionImportRow row : rowRepository.findAllByImportJobIdOrderByRowNumberAsc(importId)) {
            csv.append(row.getRowNumber()).append(',')
                    .append(CsvEscaper.escape(row.getContentVi())).append(',')
                    .append(row.getStatus()).append(',')
                    .append(CsvEscaper.escape(row.getErrorCode())).append(',')
                    .append(CsvEscaper.escape(row.getErrorMessage())).append(',')
                    .append(row.getExistingQuestion() == null ? "" : row.getExistingQuestion().getId())
                    .append(',')
                    .append(row.getCreatedQuestion() == null ? "" : row.getCreatedQuestion().getId())
                    .append("\r\n");
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }

    @Override
    public List<Long> findJobIdsByStatuses(List<QuestionImportStatus> statuses) {
        return jobRepository.findAllByStatusIn(statuses).stream()
                .map(QuestionImportJob::getId)
                .toList();
    }

    private QuestionImportJob findJob(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question import job not found"));
    }

    private static QuestionImportRow toEntity(QuestionImportJob job, QuestionCsvRecord record) {
        return QuestionImportRow.builder()
                .importJob(job)
                .rowNumber(record.rowNumber())
                .contentVi(record.contentVi())
                .contentEn(record.contentEn())
                .levelValue(record.level())
                .questionTypeValue(record.questionType())
                .difficultyValue(record.difficulty())
                .companyRef(record.companyRef())
                .techStackCodes(record.techStackCodes())
                .technologyCodes(record.technologyCodes())
                .activeValue(record.active())
                .status(QuestionImportRowStatus.PENDING)
                .build();
    }

    private static byte[] readAndValidateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new QuestionImportException("CSV file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new QuestionImportException("CSV file must not exceed 5 MB");
        }
        String name = safeFileName(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new QuestionImportException("Only .csv files are supported");
        }
        try {
            return file.getBytes();
        } catch (java.io.IOException ex) {
            throw new QuestionImportException("Cannot read uploaded file");
        }
    }

    private static String safeFileName(String original) {
        String value = original == null ? "questions.csv" : original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        String fileName = slash >= 0 ? value.substring(slash + 1) : value;
        String safe = fileName.isBlank() ? "questions.csv" : fileName;
        return safe.length() <= 255 ? safe : safe.substring(safe.length() - 255);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > PaginationConstant.MAX_PAGE_SIZE) {
            throw new QuestionImportException(
                    "Page must be non-negative and size must be between 1 and %d"
                            .formatted(PaginationConstant.MAX_PAGE_SIZE)
            );
        }
    }
}
