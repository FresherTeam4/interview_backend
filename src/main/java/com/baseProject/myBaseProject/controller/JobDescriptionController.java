package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.jobdescription.CreateJobDescriptionTextRequest;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionAnalysisResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jobdescription.JobDescriptionResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.JobDescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/job-descriptions")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Job Descriptions", description = "Upload and analyze job descriptions")
public class JobDescriptionController {
    private final JobDescriptionService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF job description and create a draft template asynchronously")
    public ResponseEntity<JobDescriptionResponse> upload(
            @CurrentUser CustomUserDetails user,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        return response(service.upload(user.getId(), file));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit job description text and create a draft template asynchronously")
    public ResponseEntity<JobDescriptionResponse> createFromText(
            @CurrentUser CustomUserDetails user,
            @Valid @RequestBody CreateJobDescriptionTextRequest request) {
        return response(service.createFromText(user.getId(), request));
    }

    @GetMapping
    public List<JobDescriptionResponse> list(@CurrentUser CustomUserDetails user) {
        return service.list(user.getId());
    }

    @GetMapping("/{id}")
    public JobDescriptionResponse get(@CurrentUser CustomUserDetails user,
                                      @PathVariable Long id) {
        return service.get(user.getId(), id);
    }

    @GetMapping("/{id}/file")
    public JobDescriptionFileUrlResponse fileUrl(@CurrentUser CustomUserDetails user,
                                                 @PathVariable Long id) {
        return service.fileUrl(user.getId(), id);
    }

    @GetMapping("/{id}/analysis")
    public JobDescriptionAnalysisResponse analysis(@CurrentUser CustomUserDetails user,
                                                   @PathVariable Long id) {
        return service.analysis(user.getId(), id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobDescriptionResponse retry(@CurrentUser CustomUserDetails user,
                                        @PathVariable Long id) {
        return service.retry(user.getId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser CustomUserDetails user, @PathVariable Long id) {
        service.delete(user.getId(), id);
    }

    private ResponseEntity<JobDescriptionResponse> response(JobDescriptionService.UploadResult result) {
        return ResponseEntity.status(result.reusedExisting() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(result.document());
    }
}
