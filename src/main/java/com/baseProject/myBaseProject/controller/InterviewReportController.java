package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.session.InterviewReportResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-sessions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Reports", description = "Read and retry interview scoring")
public class InterviewReportController {
    private final InterviewReportService service;

    @GetMapping("/{id}/report")
    @Operation(summary = "Get interview scoring status or completed report")
    public InterviewReportResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PostMapping("/{id}/scoring/retry")
    @Operation(summary = "Retry failed interview scoring")
    public ResponseEntity<InterviewReportResponse> retryScoring(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.accepted().body(service.retryScoring(user.getId(), id));
    }
}
