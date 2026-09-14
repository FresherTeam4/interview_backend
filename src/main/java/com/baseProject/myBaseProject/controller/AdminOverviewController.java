package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminOverviewResponse;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị")
public class AdminOverviewController {
    private final AdminOverviewService service;

    @GetMapping("/overview")
    @Operation(
            summary = "Lấy tổng quan hệ thống",
            description = "Trả về số liệu người dùng, phiên phỏng vấn và mẫu công khai trong kỳ đã chọn.")
    public AdminOverviewResponse get(
            @RequestParam(defaultValue = "7") int days) {
        return service.get(days);
    }
}
