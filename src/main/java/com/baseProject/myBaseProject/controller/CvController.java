package com.baseProject.myBaseProject.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.CvDocumentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST API cho việc quản lý file CV.
 * <ul>
 *   <li>US-1: Upload file CV dạng PDF</li>
 *   <li>US-2: Trigger AI bóc tách CV</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
@IsAuthenticated
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "CV", description = "Upload, xem danh sách và bóc tách CV")
public class CvController {
    private final CvDocumentService cvDocumentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload file CV (PDF)")
    public ResponseEntity<CvDocumentResponse> upload(
            @CurrentUser CustomUserDetails currentUser,
            @RequestParam("file") MultipartFile file) {
        CvDocumentResponse response = cvDocumentService.upload(currentUser.getId(), file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Danh sách CV đã upload")
    public List<CvDocumentResponse> list(@CurrentUser CustomUserDetails currentUser) {
        return cvDocumentService.list(currentUser.getId());
    }

    @GetMapping("/active")
    @Operation(summary = "CV đang được sử dụng")
    public CvDocumentResponse activeCv(@CurrentUser CustomUserDetails currentUser) {
        return cvDocumentService.activeCv(currentUser.getId());
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "Bóc tách CV bằng AI")
    public CvDocumentResponse parse(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long id) {
        return cvDocumentService.parse(currentUser.getId(), id);
    }
}
