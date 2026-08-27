package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.interview.CreateInterviewSessionRequest;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.InterviewSessionResponse;
import com.baseProject.myBaseProject.dto.interview.RetryInterviewSessionRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.InterviewSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Sessions", description = "Tạo và theo dõi phiên phỏng vấn")
public class InterviewSessionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String POLL_RETRY_AFTER_SECONDS = "1";

    private final InterviewSessionService interviewSessionService;

    @PostMapping
    @Operation(summary = "Tạo session và sinh bộ câu hỏi ở background")
    public ResponseEntity<InterviewSessionAcceptedResponse> create(
            @CurrentUser CustomUserDetails currentUser,
            @Parameter(
                    description = "Key 1-128 ký tự; dùng lại cùng body trả session đã tạo",
                    required = true)
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
            @Size(max = 128, message = "Idempotency-Key tối đa 128 ký tự")
            String idempotencyKey,
            @Valid @RequestBody CreateInterviewSessionRequest request) {
        InterviewSessionAcceptedResponse response = interviewSessionService.create(
                currentUser.getId(), idempotencyKey, request);
        return accepted(response);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Poll trạng thái pre-interview của session")
    public InterviewSessionResponse get(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId) {
        return interviewSessionService.get(currentUser.getId(), sessionId);
    }

    @PostMapping("/{sessionId}/retry")
    @Operation(summary = "Thử lại bước sinh bộ câu hỏi đã thất bại")
    public ResponseEntity<InterviewSessionAcceptedResponse> retry(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody RetryInterviewSessionRequest request) {
        InterviewSessionAcceptedResponse response = interviewSessionService.retry(
                currentUser.getId(), sessionId, request);
        return accepted(response);
    }

    private ResponseEntity<InterviewSessionAcceptedResponse> accepted(
            InterviewSessionAcceptedResponse response) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/sessions/" + response.id()))
                .header(HttpHeaders.RETRY_AFTER, POLL_RETRY_AFTER_SECONDS)
                .body(response);
    }
}
