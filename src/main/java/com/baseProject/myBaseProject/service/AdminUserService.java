package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateUserStatusRequest;
import com.baseProject.myBaseProject.enums.UserRole;

public interface AdminUserService {
    AdminPageResponse<AdminUserSummaryResponse> list(
            String keyword, UserRole role, Boolean enabled, int page, int size);

    AdminUserDetailResponse get(Long userId);

    AdminUserDetailResponse updateStatus(
            Long adminId, Long userId, UpdateUserStatusRequest request);
}
