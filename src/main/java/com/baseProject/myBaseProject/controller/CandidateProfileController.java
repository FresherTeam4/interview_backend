package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.CandidateProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@IsAuthenticated
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Hồ sơ ứng viên", description = "Xem, sửa và xác nhận hồ sơ bóc tách từ CV")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    @GetMapping
    @Operation(summary = "Danh sách hồ sơ của tôi rút gọn")
    public List<ProfileSummaryResponse> list(@CurrentUser CustomUserDetails currentUser) {
        return candidateProfileService.list(currentUser.getId());
    }


    @GetMapping("/{profileId}")
    @Operation(summary = "Xem một hồ sơ đầy đủ")
    public CandidateProfileResponse get(@CurrentUser CustomUserDetails currentUser,
                                        @PathVariable Long profileId) {
        return candidateProfileService.get(currentUser.getId(), profileId);
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Lưu hồ sơ đã sửa")
    public CandidateProfileResponse update(@CurrentUser CustomUserDetails currentUser,
                                           @PathVariable Long profileId,
                                           @Valid @RequestBody ProfileUpdateRequest request) {
        return candidateProfileService.update(currentUser.getId(), profileId, request);
    }

    @PostMapping("/{profileId}/confirm")
    @Operation(summary = "Xác nhận hồ sơ đã chính xác")
    public CandidateProfileResponse confirm(@CurrentUser CustomUserDetails currentUser,
                                            @PathVariable Long profileId) {
        return candidateProfileService.confirm(currentUser.getId(), profileId);
    }
}
