package com.baseProject.myBaseProject.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseProject.myBaseProject.dto.interview.InterviewSessionAcceptedResponse;
import com.baseProject.myBaseProject.dto.interview.SessionVersionRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.AwaitingAction;
import com.baseProject.myBaseProject.enums.SessionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.CustomUserDetailsService;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import com.baseProject.myBaseProject.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(InterviewSessionController.class)
@Import(InterviewSessionControllerTest.MethodSecurityTestConfig.class)
class InterviewSessionControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-26T07:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InterviewSessionService interviewSessionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void completeReturnsAcceptedScoringSession() throws Exception {
        SessionVersionRequest request = new SessionVersionRequest(9L);
        InterviewSessionAcceptedResponse response = new InterviewSessionAcceptedResponse(
                SESSION_ID,
                SessionStatus.SCORING,
                AwaitingAction.REPORT,
                10L,
                CREATED_AT);
        when(interviewSessionService.complete(USER_ID, SESSION_ID, request))
                .thenReturn(response);

        mockMvc.perform(post("/api/sessions/{sessionId}/complete", SESSION_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/sessions/42"))
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("SCORING"))
                .andExpect(jsonPath("$.awaitingAction").value("REPORT"))
                .andExpect(jsonPath("$.version").value(10));

        verify(interviewSessionService).complete(USER_ID, SESSION_ID, request);
    }

    @Test
    void completeRejectsNegativeExpectedVersion() throws Exception {
        mockMvc.perform(post("/api/sessions/{sessionId}/complete", SESSION_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(interviewSessionService);
    }

    @Test
    void adminCannotCompleteUserSession() throws Exception {
        mockMvc.perform(post("/api/sessions/{sessionId}/complete", SESSION_ID)
                        .with(user(userDetails(UserRole.ADMIN)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":9}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(interviewSessionService);
    }

    @Test
    void abandonReturnsTerminalSessionWithoutStartingScoring() throws Exception {
        SessionVersionRequest request = new SessionVersionRequest(9L);
        InterviewSessionAcceptedResponse response = new InterviewSessionAcceptedResponse(
                SESSION_ID,
                SessionStatus.ABANDONED,
                AwaitingAction.NONE,
                10L,
                CREATED_AT);
        when(interviewSessionService.abandon(USER_ID, SESSION_ID, request))
                .thenReturn(response);

        mockMvc.perform(post("/api/sessions/{sessionId}/abandon", SESSION_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.awaitingAction").value("NONE"));

        verify(interviewSessionService).abandon(USER_ID, SESSION_ID, request);
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
            return Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        }
    }
}
