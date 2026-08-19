package com.baseProject.myBaseProject.controller;

import java.util.List;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.question.TechnologySummaryResponse;
import com.baseProject.myBaseProject.enums.TechnologyType;
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
@RequestMapping("/api/admin/technologies")
@RequiredArgsConstructor
@IsEventAdmin
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Question Bank Admin", description = "Manage interview questions")
public class TechnologyAdminController {
    private final QuestionService questionService;

    @GetMapping
    @Operation(summary = "Get Technology options for the question form")
    public List<TechnologySummaryResponse> getAll(
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) TechnologyType type
    ) {
        return questionService.getTechnologies(activeOnly, type);
    }
}
