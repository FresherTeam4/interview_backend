package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.profile.CandidateProfileResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileSummaryResponse;
import com.baseProject.myBaseProject.dto.profile.ProfileUpdateRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
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
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Candidate Profile", description = "View, edit, and confirm profiles parsed from CVs")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    @GetMapping
    @Operation(summary = "Get current user's candidate profiles")
    public List<ProfileSummaryResponse> list(@CurrentUser CustomUserDetails currentUser) {
        return candidateProfileService.list(currentUser.getId());
    }

    @GetMapping("/{profileId}")
    @Operation(summary = "Get a candidate profile")
    public CandidateProfileResponse get(@CurrentUser CustomUserDetails currentUser,
                                        @PathVariable Long profileId) {
        return candidateProfileService.get(currentUser.getId(), profileId);
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Update a candidate profile")
    public CandidateProfileResponse update(@CurrentUser CustomUserDetails currentUser,
                                           @PathVariable Long profileId,
                                           @Valid @RequestBody ProfileUpdateRequest request) {
        return candidateProfileService.update(currentUser.getId(), profileId, request);
    }

    @PostMapping("/{profileId}/confirm")
    @Operation(summary = "Confirm a candidate profile for interview use")
    public CandidateProfileResponse confirm(@CurrentUser CustomUserDetails currentUser,
                                            @PathVariable Long profileId) {
        return candidateProfileService.confirm(currentUser.getId(), profileId);
    }
}
