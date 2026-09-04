package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.dto.cv.CvFileUrlResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping("/{cvId}")
    @Operation(summary = "Get a CV and its parsing status")
    public CvDocumentResponse get(@CurrentUser CustomUserDetails currentUser,
                                  @PathVariable Long cvId) {
        return cvDocumentService.get(currentUser.getId(), cvId);
    }

    @GetMapping("/{cvId}/file")
    @Operation(summary = "Get a temporary URL for the original CV file")
    public CvFileUrlResponse fileUrl(@CurrentUser CustomUserDetails currentUser,
                                     @PathVariable Long cvId) {
        return cvDocumentService.fileUrl(currentUser.getId(), cvId);
    }

    @PostMapping("/{cvId}/parse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Retry parsing a failed CV")
    public CvDocumentResponse retryParse(@CurrentUser CustomUserDetails currentUser,
                                         @PathVariable Long cvId) {
        return cvDocumentService.retryParse(currentUser.getId(), cvId);
    }

    @DeleteMapping("/{cvId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a CV from the current user's list")
    public void delete(@CurrentUser CustomUserDetails currentUser,
                       @PathVariable Long cvId) {
        cvDocumentService.delete(currentUser.getId(), cvId);
    }
}
