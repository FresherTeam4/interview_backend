package com.baseProject.myBaseProject.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.UpdateCandidateProfileRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.CandidateProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API cho hồ sơ ứng viên (US-3).
 * <ul>
 *   <li>Xem hồ sơ AI đã bóc tách</li>
 *   <li>Sửa lại thông tin AI bóc tách sai</li>
 *   <li>Xác nhận hồ sơ đúng để bắt đầu buổi phỏng vấn</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@IsAuthenticated
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Candidate Profile", description = "Xem, sửa và xác nhận hồ sơ ứng viên")
public class CandidateProfileController {
    private final CandidateProfileService candidateProfileService;

    @GetMapping
    @Operation(summary = "Xem hồ sơ ứng viên hiện tại")
    public CandidateProfileResponse currentProfile(@CurrentUser CustomUserDetails currentUser) {
        return candidateProfileService.currentProfile(currentUser.getId());
    }

    @PutMapping
    @Operation(summary = "Sửa hồ sơ ứng viên")
    public CandidateProfileResponse update(
            @CurrentUser CustomUserDetails currentUser,
            @Valid @RequestBody UpdateCandidateProfileRequest request) {
        return candidateProfileService.update(currentUser.getId(), request);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Xác nhận hồ sơ đúng, cho phép bắt đầu phỏng vấn")
    public CandidateProfileResponse confirm(@CurrentUser CustomUserDetails currentUser) {
        return candidateProfileService.confirm(currentUser.getId());
    }
}
