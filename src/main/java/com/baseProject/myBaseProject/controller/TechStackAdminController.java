package com.baseProject.myBaseProject.controller;

import java.util.List;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.question.TechStackSummaryResponse;
import com.baseProject.myBaseProject.security.authorization.IsEventAdmin;
import com.baseProject.myBaseProject.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tech-stacks")
@RequiredArgsConstructor
@IsEventAdmin
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Question Bank Admin", description = "Manage interview questions")
public class TechStackAdminController {
    private final QuestionService questionService;

    @GetMapping
    @Operation(summary = "Get Tech Stack options for the question form")
    public List<TechStackSummaryResponse> getAll(
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        return questionService.getTechStacks(activeOnly);
    }
}
