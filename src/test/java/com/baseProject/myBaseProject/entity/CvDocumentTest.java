package com.baseProject.myBaseProject.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baseProject.myBaseProject.enums.CvDocumentStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class CvDocumentTest {

    @Test
    void transitionsEncapsulateStatusAndActivityChanges() {
        Instant firstParsedAt = Instant.parse("2026-08-26T01:00:00Z");
        CvDocument document = CvDocument.builder()
                .status(CvDocumentStatus.FAILED)
                .statusMessage("old failure")
                .active(false)
                .parsedAt(firstParsedAt)
                .build();

        document.reactivate();
        assertTrue(document.isActive());

        document.prepareForRetry();
        assertEquals(CvDocumentStatus.UPLOADED, document.getStatus());
        assertNull(document.getStatusMessage());

        document.markParsing();
        assertEquals(CvDocumentStatus.PARSING, document.getStatus());

        document.markFailed("new failure");
        assertEquals(CvDocumentStatus.FAILED, document.getStatus());
        assertEquals("new failure", document.getStatusMessage());

        document.markParsed(Instant.parse("2026-08-26T02:00:00Z"));
        assertEquals(CvDocumentStatus.PARSED, document.getStatus());
        assertNull(document.getStatusMessage());
        assertEquals(firstParsedAt, document.getParsedAt());

        document.deactivate();
        assertFalse(document.isActive());
    }
}
