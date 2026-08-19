package com.baseProject.myBaseProject.controller;

import java.net.URI;
import java.util.List;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionFilter;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.dto.question.QuestionUpdateRequest;
import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsEventAdmin;
import com.baseProject.myBaseProject.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
@IsEventAdmin
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Question Bank Admin", description = "Manage interview questions")
public class QuestionAdminController {
    private final QuestionService questionService;

    @PostMapping
    @Operation(summary = "Create a question")
    public ResponseEntity<QuestionResponse> create(
            @Valid @RequestBody QuestionCreateRequest request,
            @CurrentUser CustomUserDetails currentUser
    ) {
        QuestionResponse response = questionService.create(request, currentUser.getId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a question by id")
    public QuestionResponse getById(@PathVariable Long id) {
        return questionService.getById(id);
    }

    @GetMapping
    @Operation(summary = "Get a paginated question list")
    public PageResponse<QuestionResponse> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) List<Integer> techStackIds,
            @RequestParam(required = false) Boolean unclassified,
            @RequestParam(required = false) List<Integer> technologyIds,
            @RequestParam(required = false) List<QuestionLevel> levels,
            @RequestParam(required = false) List<QuestionType> questionTypes,
            @RequestParam(required = false) List<QuestionDifficulty> difficulties,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        QuestionFilter filter = new QuestionFilter(
                keyword,
                active,
                techStackIds,
                unclassified,
                technologyIds,
                levels,
                questionTypes,
                difficulties
        );
        return questionService.search(filter, page, size);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Replace a question",
            description = "Send the version returned by the latest GET response to prevent lost updates."
    )
    public QuestionResponse update(
            @PathVariable Long id,
            @Valid @RequestBody QuestionUpdateRequest request
    ) {
        return questionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Deactivate a question",
            description = "Performs a soft delete by setting is_active to false so interview history remains valid."
    )
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
