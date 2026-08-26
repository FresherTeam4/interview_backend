package com.baseProject.myBaseProject.mapper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import com.baseProject.myBaseProject.enums.CvDocumentStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class CvDocumentResponseMapperTest {

    private final CvDocumentMapper mapper = new CvDocumentMapper();

    @Test
    void mapsDocumentAndProfileWithoutLoadingAnything() {
        Instant uploadedAt = Instant.parse("2026-08-26T01:00:00Z");
        Instant parsedAt = Instant.parse("2026-08-26T01:01:00Z");
        CvDocument document = CvDocument.builder()
                .id(10L)
                .originalFilename("backend-cv.pdf")
                .contentType("application/pdf")
                .fileSizeBytes(1234L)
                .status(CvDocumentStatus.PARSED)
                .uploadedAt(uploadedAt)
                .parsedAt(parsedAt)
                .build();
        CandidateProfile profile = CandidateProfile.builder()
                .id(20L)
                .headline("Java Backend Developer")
                .confirmedAt(parsedAt)
                .build();

        CvDocumentResponse response = mapper.toResponse(document, profile);

        assertAll(
                () -> assertEquals(10L, response.id()),
                () -> assertEquals("backend-cv.pdf", response.originalFilename()),
                () -> assertEquals(CvDocumentStatus.PARSED, response.status()),
                () -> assertEquals(uploadedAt, response.uploadedAt()),
                () -> assertEquals(parsedAt, response.parsedAt()),
                () -> assertEquals(20L, response.profileId()),
                () -> assertTrue(response.profileConfirmed()),
                () -> assertEquals("Java Backend Developer", response.profileHeadline()));
    }

    @Test
    void mapsMissingProfileAsEmptyProfileFields() {
        CvDocument document = CvDocument.builder()
                .id(10L)
                .status(CvDocumentStatus.UPLOADED)
                .build();

        CvDocumentResponse response = mapper.toResponse(document, null);

        assertAll(
                () -> assertNull(response.profileId()),
                () -> assertFalse(response.profileConfirmed()),
                () -> assertNull(response.profileHeadline()));
    }
}
