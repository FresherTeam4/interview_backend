package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.config.OpenApiConfig;
import com.baseProject.myBaseProject.dto.speech.SpeechTranscriptionResponse;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.authorization.CurrentUser;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.SpeechService;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/interview-sessions/{sessionId}/speech")
@IsAuthenticated
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Interview Speech", description = "Provider-independent interview speech APIs")
public class SpeechController {
    private final SpeechService service;

    @PostMapping(
            value = "/transcriptions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Transcribe a candidate answer in the session language")
    public SpeechTranscriptionResponse transcribe(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long sessionId,
            @RequestPart("audio") MultipartFile audio) {
        SpeechTranscriptionResult result = service.transcribe(
                user.getId(), sessionId, audio);

        return new SpeechTranscriptionResponse(
                result.text(), result.languageCode());
    }

    @PostMapping(
            value = "/turns/{turnId}/audio",
            produces = "audio/mpeg")
    @Operation(summary = "Generate or retrieve speech for an interviewer turn")
    public ResponseEntity<byte[]> interviewerAudio(
            @CurrentUser CustomUserDetails user,
            @PathVariable Long sessionId,
            @Parameter(description = "Database id of an INTERVIEWER turn, not its turnIndex")
            @PathVariable Long turnId) {
        SpeechSynthesisResult result = service.synthesizeInterviewerTurn(
                user.getId(), sessionId, turnId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(result.audio());
    }
}
