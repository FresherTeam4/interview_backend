package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.admin.AdminOverviewResponse;

public interface AdminOverviewService {
    AdminOverviewResponse get(int days);
}
