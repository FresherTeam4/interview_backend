package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateUserStatusRequest;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@IsAdmin
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Quản trị")
public class AdminUserController {
    private final AdminUserService service;

    @GetMapping
    @Operation(
            summary = "Lấy danh sách người dùng",
            description = "Tìm kiếm và lọc tài khoản theo vai trò, trạng thái hoạt động.")
    public AdminPageResponse<AdminUserSummaryResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, role, enabled, page, size);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Lấy chi tiết người dùng",
            description = "Trả về thông tin tài khoản và số lượng dữ liệu nghiệp vụ liên quan.")
    public AdminUserDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Khóa hoặc mở tài khoản người dùng",
            description = "Chỉ thay đổi được tài khoản USER; khi khóa sẽ thu hồi mọi refresh token.")
    public AdminUserDetailResponse updateStatus(
            @CurrentUser CustomUserDetails admin,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return service.updateStatus(admin.getId(), id, request);
    }
}
