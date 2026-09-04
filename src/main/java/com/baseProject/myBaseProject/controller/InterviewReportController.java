package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.report.InterviewReportResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.InterviewReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Reports", description = "Đọc kết quả chấm điểm phỏng vấn")
public class InterviewReportController {

    private final InterviewReportService reportService;

    @GetMapping("/{sessionId}/report")
    @Operation(summary = "Đọc báo cáo scoring đã hoàn tất")
    public InterviewReportResponse get(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId) {
        return reportService.get(currentUser.getId(), sessionId);
    }
}
