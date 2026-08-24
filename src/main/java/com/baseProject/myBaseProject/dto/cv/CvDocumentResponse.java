package com.baseProject.myBaseProject.dto.cv;

import java.time.Instant;

import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;

public record CvDocumentResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        CvDocumentStatus status,
        String statusMessage,
        boolean active,
        Instant uploadedAt,
        Instant parsedAt
) {
    public static CvDocumentResponse from(CvDocument document) {
        return new CvDocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getStatus(),
                document.getStatusMessage(),
                document.isActive(),
                document.getUploadedAt(),
                document.getParsedAt()
        );
    }
}
