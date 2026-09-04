package com.baseProject.myBaseProject.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptUploadRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.CustomUserDetailsService;
import com.baseProject.myBaseProject.service.JwtService;
import com.baseProject.myBaseProject.service.VoiceAttemptService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(VoiceAttemptController.class)
@Import(VoiceAttemptControllerTest.MethodSecurityTestConfig.class)
class VoiceAttemptControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final Long ATTEMPT_ID = 301L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VoiceAttemptService voiceAttemptService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void uploadReturnsAcceptedRecordedAttempt() throws Exception {
        VoiceAttemptUploadRequest metadata =
                new VoiceAttemptUploadRequest(205L, "attempt-1", 5_000, 9L);
        VoiceAttemptResponse response = response();
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(metadata));
        MockMultipartFile audioPart = new MockMultipartFile(
                "file",
                "voice.webm",
                "audio/webm",
                new byte[]{1, 2, 3});
        when(voiceAttemptService.upload(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        org.mockito.ArgumentMatchers.eq(metadata),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/sessions/{sessionId}/voice-attempts", SESSION_ID)
                        .file(metadataPart)
                        .file(audioPart)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Location",
                        "/api/sessions/42/voice-attempts/301"))
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.attemptNo").value(1));
    }

    @Test
    void invalidMetadataDoesNotCallService() throws Exception {
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"promptTurnId\":205,\"clientAttemptId\":\"\",\"durationMs\":5000,"
                        .concat("\"expectedVersion\":9}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile audioPart = new MockMultipartFile(
                "file", "voice.webm", "audio/webm", new byte[]{1});

        mockMvc.perform(multipart("/api/sessions/{sessionId}/voice-attempts", SESSION_ID)
                        .file(metadataPart)
                        .file(audioPart)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(voiceAttemptService);
    }

    @Test
    void getReturnsOwnedAttempt() throws Exception {
        when(voiceAttemptService.get(USER_ID, SESSION_ID, ATTEMPT_ID))
                .thenReturn(response());

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/voice-attempts/{attemptId}",
                        SESSION_ID,
                        ATTEMPT_ID)
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.promptTurnId").value(205));

        verify(voiceAttemptService).get(USER_ID, SESSION_ID, ATTEMPT_ID);
    }

    @Test
    void adminCannotUploadUserRecording() throws Exception {
        MockMultipartFile metadataPart = new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"promptTurnId\":205,\"clientAttemptId\":\"attempt-1\","
                        .concat("\"durationMs\":5000,\"expectedVersion\":9}")
                        .getBytes(StandardCharsets.UTF_8));
        MockMultipartFile audioPart = new MockMultipartFile(
                "file", "voice.webm", "audio/webm", new byte[]{1});

        mockMvc.perform(multipart("/api/sessions/{sessionId}/voice-attempts", SESSION_ID)
                        .file(metadataPart)
                        .file(audioPart)
                        .with(user(userDetails(UserRole.ADMIN)))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(voiceAttemptService);
    }

    private VoiceAttemptResponse response() {
        return new VoiceAttemptResponse(
                ATTEMPT_ID,
                205L,
                (short) 1,
                VoiceAttemptStatus.RECORDED,
                0,
                null,
                null,
                5_000,
                null,
                NOW);
    }

    private static CustomUserDetails userDetails(UserRole role) {
        return new CustomUserDetails(UserAccount.builder()
                .id(USER_ID)
                .email("user@example.com")
                .passwordHash("password")
                .role(role)
                .enabled(true)
                .build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
