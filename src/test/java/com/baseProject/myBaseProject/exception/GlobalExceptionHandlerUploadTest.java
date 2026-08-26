package com.baseProject.myBaseProject.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.baseProject.myBaseProject.constant.Message;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class GlobalExceptionHandlerUploadTest {

    @Test
    void maxMultipartSizeUsesFeatureNeutralErrorCode() {
        Instant now = Instant.parse("2026-08-26T08:00:00Z");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                Clock.fixed(now, ZoneOffset.UTC));
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/job-descriptions/file");

        ResponseEntity<ApiError> response = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(6_000_000L), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).isNotNull().satisfies(error -> {
            assertThat(error.timestamp()).isEqualTo(now);
            assertThat(error.code()).isEqualTo(ErrorCode.UPLOAD_TOO_LARGE);
            assertThat(error.message()).isEqualTo(Message.UPLOAD_TOO_LARGE);
            assertThat(error.path()).isEqualTo("/api/job-descriptions/file");
        });
    }
}
