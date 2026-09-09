package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.jobdescription.CreateJobDescriptionTextRequest;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionAnalysisResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.JobDescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/job-descriptions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Mô tả công việc", description = "Tạo, phân tích và quản lý mô tả công việc")
public class JobDescriptionController {
    private final JobDescriptionService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tải lên mô tả công việc",
            description = "Tải lên tệp PDF, phân tích nội dung và tạo mẫu phỏng vấn nháp ở chế độ nền.")
    public ResponseEntity<JobDescriptionResponse> upload(
            @CurrentUser CustomUserDetails user,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        return response(service.upload(user.getId(), file));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Tạo mô tả công việc từ văn bản",
            description = "Gửi nội dung văn bản để phân tích và tạo mẫu phỏng vấn nháp ở chế độ nền.")
    public ResponseEntity<JobDescriptionResponse> createFromText(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody CreateJobDescriptionTextRequest request) {
        return response(service.createFromText(user.getId(), request));
    }

    @GetMapping
    @Operation(
            summary = "Lấy danh sách mô tả công việc",
            description = "Trả về các mô tả công việc của người dùng hiện tại cùng trạng thái xử lý.")
    public List<JobDescriptionResponse> list(@CurrentUser CustomUserDetails user) {
        return service.list(user.getId());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy chi tiết mô tả công việc",
            description = "Trả về thông tin, trạng thái xử lý và mẫu phỏng vấn liên quan của một mô tả công việc.")
    public JobDescriptionResponse get(@CurrentUser CustomUserDetails user,
                                      @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @GetMapping("/{id}/file")
    @Operation(
            summary = "Lấy đường dẫn tệp mô tả công việc",
            description = "Tạo URL tạm thời để tải hoặc xem tệp PDF gốc của mô tả công việc.")
    public JobDescriptionFileUrlResponse fileUrl(@CurrentUser CustomUserDetails user,
                                                 @PathVariable Long id) {
        return service.fileUrl(user.getId(), id);
    }

    @GetMapping("/{id}/analysis")
    @Operation(
            summary = "Lấy kết quả phân tích công việc",
            description = "Trả về văn bản đã trích xuất và kết quả phân tích AI của mô tả công việc đã xử lý xong.")
    public JobDescriptionAnalysisResponse analysis(@CurrentUser CustomUserDetails user,
                                                   @PathVariable Long id) {
        return service.analysis(user.getId(), id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Thử lại xử lý mô tả công việc",
            description = "Khởi động lại quá trình phân tích và tạo mẫu phỏng vấn cho bản ghi đã xử lý thất bại.")
    public JobDescriptionResponse retry(@CurrentUser CustomUserDetails user,
                                        @PathVariable Long id) {
        return service.retry(user.getId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Xóa mô tả công việc",
            description = "Xóa mềm mô tả công việc khỏi danh sách của người dùng hiện tại.")
    public void delete(@CurrentUser CustomUserDetails user, @PathVariable Long id) {
        service.delete(user.getId(), id);
    }

    private ResponseEntity<JobDescriptionResponse> response(JobDescriptionService.UploadResult result) {
        return ResponseEntity.status(result.reusedExisting() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(result.document());
    }
}
