package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptConfirmRequest;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptUploadRequest;
import com.baseProject.myBaseProject.dto.interview.VoiceTranscriptUpdateRequest;
import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsUser;
import com.baseProject.myBaseProject.service.VoiceAttemptService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/sessions/{sessionId}/voice-attempts")
@RequiredArgsConstructor
@IsUser
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Voice Attempts", description = "Upload và đọc bản ghi voice turn-based")
public class VoiceAttemptController {

    private static final String POLL_RETRY_AFTER_SECONDS = "1";

    private final VoiceAttemptService voiceAttemptService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload một recording cho prompt hiện tại")
    public ResponseEntity<VoiceAttemptResponse> upload(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestPart("metadata") VoiceAttemptUploadRequest metadata,
            @RequestPart("file") MultipartFile file) {
        VoiceAttemptResponse response = voiceAttemptService.upload(
                currentUser.getId(),
                sessionId,
                metadata,
                file);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create(
                        "/api/sessions/%d/voice-attempts/%d"
                                .formatted(sessionId, response.id())))
                .header(HttpHeaders.RETRY_AFTER, POLL_RETRY_AFTER_SECONDS)
                .body(response);
    }

    @GetMapping("/{attemptId}")
    @Operation(summary = "Đọc trạng thái và metadata của một voice attempt")
    public VoiceAttemptResponse get(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId,
            @PathVariable Long attemptId) {
        return voiceAttemptService.get(currentUser.getId(), sessionId, attemptId);
    }

    @PutMapping("/{attemptId}/transcript")
    @Operation(summary = "Sửa transcript trước khi xác nhận")
    public VoiceAttemptResponse editTranscript(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId,
            @PathVariable Long attemptId,
            @Valid @RequestBody VoiceTranscriptUpdateRequest request) {
        return voiceAttemptService.editTranscript(
                currentUser.getId(),
                sessionId,
                attemptId,
                request);
    }

    @PostMapping("/{attemptId}/confirm")
    @Operation(summary = "Xác nhận transcript và gửi câu trả lời voice vào interview workflow")
    public ResponseEntity<TextAnswerAcceptedResponse> confirm(
            @CurrentUser CustomUserDetails currentUser,
            @PathVariable Long sessionId,
            @PathVariable Long attemptId,
            @Valid @RequestBody VoiceAttemptConfirmRequest request) {
        TextAnswerAcceptedResponse response = voiceAttemptService.confirm(
                currentUser.getId(),
                sessionId,
                attemptId,
                request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/sessions/" + sessionId))
                .header(HttpHeaders.RETRY_AFTER, POLL_RETRY_AFTER_SECONDS)
                .body(response);
    }
}
