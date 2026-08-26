package com.baseProject.myBaseProject.dto.cv;

import com.baseProject.myBaseProject.enums.CvDocumentStatus;

import java.time.Instant;

public record CvDocumentResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        CvDocumentStatus status,
        String statusMessage,

        Instant uploadedAt,
        Instant parsedAt,
        Long profileId,

        boolean profileConfirmed,
        String profileHeadline
) {
}
