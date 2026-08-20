package com.baseProject.myBaseProject.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.imports.QuestionImportRowResponse;
import com.baseProject.myBaseProject.dto.question.imports.QuestionImportSummaryResponse;
import com.baseProject.myBaseProject.enums.QuestionImportRowStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.QuestionImportAsyncWorker;
import com.baseProject.myBaseProject.service.QuestionImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/question-imports")
@RequiredArgsConstructor
@IsAdmin
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Question Import Admin", description = "Validate and import question CSV files")
public class QuestionImportAdminController {
    private static final String TEMPLATE = "content_vi,content_en,level,question_type,"
            + "difficulty,company_ref,tech_stack_codes,technology_codes,active\r\n"
            + "\"REST API là gì?\",\"What is a REST API?\",JUNIOR,TECHNICAL,"
            + "EASY,,BACKEND,\"JAVA|SPRING_BOOT\",true\r\n";

    private final QuestionImportService importService;
    private final QuestionImportAsyncWorker asyncWorker;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CSV and start asynchronous validation")
    public ResponseEntity<QuestionImportSummaryResponse> upload(
            @RequestPart("file") MultipartFile file,
            @CurrentUser CustomUserDetails currentUser
    ) {
        QuestionImportSummaryResponse response = importService.stage(file, currentUser.getId());
        asyncWorker.validateAsync(response.id());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.accepted().location(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get import progress and counters")
    public QuestionImportSummaryResponse getSummary(@PathVariable Long id) {
        return importService.getSummary(id);
    }

    @GetMapping("/{id}/rows")
    @Operation(summary = "Get paginated row-level validation/import results")
    public PageResponse<QuestionImportRowResponse> getRows(
            @PathVariable Long id,
            @RequestParam(required = false) QuestionImportRowStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return importService.getRows(id, status, page, size);
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "Confirm and asynchronously import all VALID rows")
    public ResponseEntity<QuestionImportSummaryResponse> commit(@PathVariable Long id) {
        QuestionImportSummaryResponse response = importService.prepareCommit(id);
        asyncWorker.importAsync(id);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping(value = "/template", produces = "text/csv")
    @Operation(summary = "Download the UTF-8 CSV template")
    public ResponseEntity<byte[]> downloadTemplate() {
        return csvDownload("question-import-template.csv", utf8WithBom(TEMPLATE));
    }

    @GetMapping(value = "/{id}/result.csv", produces = "text/csv")
    @Operation(summary = "Download row-level import results as CSV")
    public ResponseEntity<byte[]> downloadResult(@PathVariable Long id) {
        return csvDownload("question-import-%d-result.csv".formatted(id), importService.exportResult(id));
    }

    private static ResponseEntity<byte[]> csvDownload(String fileName, byte[] body) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static byte[] utf8WithBom(String value) {
        byte[] content = value.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[content.length + 3];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(content, 0, result, 3, content.length);
        return result;
    }
}
