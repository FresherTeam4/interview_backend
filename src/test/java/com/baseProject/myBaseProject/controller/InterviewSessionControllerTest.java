package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.dto.session.InterviewFocusAreaResponse;
import com.baseProject.myBaseProject.dto.session.InterviewOptionResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionResponse;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewEvidenceStatus;
import com.baseProject.myBaseProject.enums.InterviewFocusPriority;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.InterviewSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewSessionControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-06T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewSessionService service;

    @Test
    void authenticatedUserCanCreateSession() throws Exception {
        when(service.create(eq(7L), eq("request-1"), any()))
                .thenReturn(response(InterviewSessionStatus.PREPARING));

        mockMvc.perform(post("/api/interview-sessions")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": 101,
                                  "profileId": 35,
                                  "languageCode": "vi",
                                  "durationMinutes": 30,
                                  "interviewerStyle": "PROFESSIONAL"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.durationMinutes").value(30));

        verify(service).create(eq(7L), eq("request-1"), any());
    }

    @Test
    void invalidCreateBodyDoesNotReachService() throws Exception {
        mockMvc.perform(post("/api/interview-sessions")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "request-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateId": 101,
                                  "languageCode": "vi",
                                  "durationMinutes": 30,
                                  "interviewerStyle": "PROFESSIONAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.profileId").exists());

        verifyNoInteractions(service);
    }

    @Test
    void userCanReadAvailableOptions() throws Exception {
        when(service.options()).thenReturn(new InterviewSessionOptionsResponse(
                List.of(new InterviewOptionResponse("vi", "Tiếng Việt")),
                List.of(15, 30),
                List.of(new InterviewOptionResponse("PROFESSIONAL", "Chuyên nghiệp"))));

        mockMvc.perform(get("/api/interview-session-options").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[0].code").value("vi"))
                .andExpect(jsonPath("$.durations[1]").value(30))
                .andExpect(jsonPath("$.interviewerStyles[0].code")
                        .value("PROFESSIONAL"));
    }

    @Test
    void anonymousUserCannotReadSession() throws Exception {
        mockMvc.perform(get("/api/interview-sessions/501"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    private InterviewSessionResponse response(InterviewSessionStatus status) {
        return new InterviewSessionResponse(
                501L, 0L, 101L, "Backend Java", 35L, "Minh profile",
                status, "vi", 30, InterviewerStyle.PROFESSIONAL,
                null, null, null, null, null,
                null, null, NOW, NOW,
                List.of(new InterviewFocusAreaResponse(
                        "JAVA", "Java", "Java experience", InterviewFocusPriority.HIGH,
                        "Required by JD", 300, InterviewEvidenceStatus.NOT_EXPLORED,
                        (short) 0)));
    }

    private CustomUserDetails userDetails() {
        return new CustomUserDetails(UserAccount.builder()
                .id(7L)
                .email("candidate@example.com")
                .passwordHash("password")
                .role(UserRole.USER)
                .enabled(true)
                .build());
    }
}
