package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.common.PageableRequest;
import com.baseProject.myBaseProject.dto.jd.CreateTextJobDescriptionRequest;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionSummaryResponse;
import com.baseProject.myBaseProject.dto.jd.UpdateJobDescriptionRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.JobDescriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/job-descriptions")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Job Description", description = "Tạo và xác nhận JD dùng cho Interview Engine")
public class JobDescriptionController {

    private final JobDescriptionService jobDescriptionService;

    @PostMapping("/text")
    @Operation(summary = "Tạo JD dạng text")
    public ResponseEntity<JobDescriptionResponse> createText(
            @CurrentUser CustomUserDetails currentUser,
            @Valid @RequestBody CreateTextJobDescriptionRequest request) {
        JobDescriptionResponse response =
                jobDescriptionService.createText(currentUser.getId(), request);
        URI location = URI.create("/api/job-descriptions/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tạo JD từ file PDF hoặc TXT")
    public ResponseEntity<JobDescriptionResponse> createFile(
            @CurrentUser CustomUserDetails currentUser,
            @RequestPart(name = "title", required = false)
            @Size(max = 200, message = "Tiêu đề JD tối đa 200 ký tự") String title,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        JobDescriptionResponse response =
                jobDescriptionService.createFile(currentUser.getId(), title, file);
        URI location = URI.create("/api/job-descriptions/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "Danh sách JD chưa xóa")
    public PageResponse<JobDescriptionSummaryResponse> list(
            @CurrentUser CustomUserDetails currentUser,
            @Valid @ModelAttribute PageableRequest pageable) {
        return jobDescriptionService.list(currentUser.getId(), pageable.page(), pageable.size());
    }

    @GetMapping("/{jobDescriptionId}")
    @Operation(summary = "Xem đầy đủ một JD")
    public JobDescriptionResponse get(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long jobDescriptionId) {
        return jobDescriptionService.get(currentUser.getId(), jobDescriptionId);
    }

    @GetMapping("/{jobDescriptionId}/file")
    @Operation(summary = "Lấy link có hạn để xem file JD gốc")
    public JobDescriptionFileUrlResponse fileUrl(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long jobDescriptionId) {
        return jobDescriptionService.fileUrl(currentUser.getId(), jobDescriptionId);
    }

    @PutMapping("/{jobDescriptionId}")
    @Operation(summary = "Sửa JD draft")
    public JobDescriptionResponse update(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long jobDescriptionId,
            @Valid @RequestBody UpdateJobDescriptionRequest request) {
        return jobDescriptionService.update(currentUser.getId(), jobDescriptionId, request);
    }

    @PostMapping("/{jobDescriptionId}/confirm")
    @Operation(summary = "Xác nhận JD")
    public JobDescriptionResponse confirm(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long jobDescriptionId) {
        return jobDescriptionService.confirm(currentUser.getId(), jobDescriptionId);
    }

    @DeleteMapping("/{jobDescriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa mềm JD")
    public void delete(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long jobDescriptionId) {
        jobDescriptionService.delete(currentUser.getId(), jobDescriptionId);
    }
}
