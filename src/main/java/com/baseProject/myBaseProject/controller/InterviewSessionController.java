package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.session.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-sessions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Sessions", description = "Create and prepare interview sessions")
public class InterviewSessionController {
    private final InterviewSessionService service;

    @PostMapping
    @Operation(summary = "Create a session and prepare its adaptive interview plan")
    public ResponseEntity<InterviewSessionStatusResponse> create(
            @CurrentUser CustomUserDetails user,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateInterviewSessionRequest request) {
        return ResponseEntity.accepted()
                .body(service.create(user.getId(), idempotencyKey, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get interview session preparation status")
    public InterviewSessionStatusResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PostMapping("/{id}/preparation/retry")
    @Operation(summary = "Retry a failed interview preparation")
    public ResponseEntity<InterviewSessionStatusResponse> retryPreparation(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.accepted()
                .body(service.retryPreparation(user.getId(), id));
    }
}
