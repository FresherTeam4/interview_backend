package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionSummaryResponse;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminInterviewSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/interview-sessions")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị")
public class AdminInterviewSessionController {
    private final AdminInterviewSessionService service;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách phiên phỏng vấn",
            description = "Tìm kiếm và lọc mọi phiên để theo dõi trạng thái xử lý.")
    public AdminPageResponse<AdminSessionSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) InterviewSessionStatus status,
            @RequestParam(required = false) InterviewSessionMode mode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, status, mode, from, to, page, size);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy chi tiết phiên phỏng vấn",
            description = "Trả về metadata xử lý, lỗi và lịch sử chuyển trạng thái của phiên.")
    public AdminSessionDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/preparation/retry")
    @Operation(
            summary = "Thử lại bước chuẩn bị",
            description = "Khởi động lại preparation cho phiên PREPARATION_FAILED.")
    public ResponseEntity<AdminSessionDetailResponse> retryPreparation(
            @CurrentUser CustomUserDetails admin,
            @PathVariable Long id) {
        return ResponseEntity.accepted()
                .body(service.retryPreparation(admin.getId(), id));
    }

    @PostMapping("/{id}/scoring/retry")
    @Operation(
            summary = "Thử lại bước chấm điểm",
            description = "Khởi động lại scoring cho phiên SCORING_FAILED.")
    public ResponseEntity<AdminSessionDetailResponse> retryScoring(
            @CurrentUser CustomUserDetails admin,
            @PathVariable Long id) {
        return ResponseEntity.accepted()
                .body(service.retryScoring(admin.getId(), id));
    }
}
