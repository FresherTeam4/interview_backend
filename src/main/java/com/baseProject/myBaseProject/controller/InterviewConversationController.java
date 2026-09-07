package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.session.InterviewAnswerResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.SubmitInterviewAnswerRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "Interview Conversations", description = "Run adaptive text interviews")
public class InterviewConversationController {
    private final InterviewConversationService service;

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a prepared interview and its server-side timer")
    public InterviewConversationResponse start(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.start(user.getId(), id);
    }

    @GetMapping("/{id}/conversation")
    @Operation(summary = "Resume an interview from its persisted conversation")
    public InterviewConversationResponse get(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PostMapping("/{id}/answers")
    @Operation(summary = "Save a candidate answer and generate the next interviewer turn")
    public InterviewAnswerResponse answer(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubmitInterviewAnswerRequest request) {
        return service.answer(user.getId(), id, idempotencyKey, request);
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "End an interview early and continue to scoring")
    public InterviewConversationResponse finish(
            @CurrentUser CustomUserDetails user, @PathVariable Long id) {
        return service.finish(user.getId(), id);
    }
}
