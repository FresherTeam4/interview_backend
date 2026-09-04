package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.CvDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/cvs")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "CV", description = "Upload and process candidate CVs")
public class CvController {

    private final CvDocumentService cvDocumentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a CV and start background parsing")
    public ResponseEntity<CvDocumentResponse> upload(
            @CurrentUser CustomUserDetails currentUser,
            @RequestPart(name = "file", required = false) MultipartFile file) {
        CvDocumentService.CvUploadResult result =
                cvDocumentService.upload(currentUser.getId(), file);

        HttpStatus status = result.reusedExisting() ? HttpStatus.OK : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(result.document());
    }

    @GetMapping
    @Operation(summary = "Get current user's CVs")
    public List<CvDocumentResponse> list(@CurrentUser CustomUserDetails currentUser) {
        return cvDocumentService.list(currentUser.getId());
    }
}
