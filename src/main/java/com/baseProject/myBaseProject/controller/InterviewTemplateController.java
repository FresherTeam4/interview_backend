package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.template.ConfirmInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateResponse;
import com.baseProject.myBaseProject.dto.template.InterviewTemplateSummaryResponse;
import com.baseProject.myBaseProject.dto.template.PublishInterviewTemplateRequest;
import com.baseProject.myBaseProject.dto.template.TemplatePageResponse;
import com.baseProject.myBaseProject.dto.template.TemplateStateRequest;
import com.baseProject.myBaseProject.dto.template.UpdateInterviewTemplateRequest;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAdmin;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.InterviewTemplateService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview-templates")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Templates", description = "Edit and confirm AI-generated interview templates")
public class InterviewTemplateController {
    private final InterviewTemplateService service;

    @GetMapping
    public TemplatePageResponse<InterviewTemplateSummaryResponse> list(
            @CurrentUser CustomUserDetails user,
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user.getId(), scope, page, size);
    }

    @GetMapping("/{id}")
    public InterviewTemplateResponse get(@CurrentUser CustomUserDetails user,
                                         @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit an unconfirmed interview template")
    public InterviewTemplateResponse update(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody UpdateInterviewTemplateRequest request) {
        return service.update(user.getId(), id, request);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm and freeze an interview template")
    public InterviewTemplateResponse confirm(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody ConfirmInterviewTemplateRequest request) {
        return service.confirm(user.getId(), id, request);
    }

    @PostMapping("/{id}/publish")
    @IsAdmin
    public InterviewTemplateResponse publish(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody PublishInterviewTemplateRequest request) {
        return service.publish(user.getId(), id, request);
    }

    @PostMapping("/{id}/unpublish")
    @IsAdmin
    public InterviewTemplateResponse unpublish(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateStateRequest request) {
        return service.unpublish(user.getId(), id, request.expectedVersion());
    }

    @PostMapping("/{id}/archive")
    public InterviewTemplateResponse archive(
            @CurrentUser CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody TemplateStateRequest request) {
        return service.archive(user.getId(), id, request.expectedVersion());
    }
}
