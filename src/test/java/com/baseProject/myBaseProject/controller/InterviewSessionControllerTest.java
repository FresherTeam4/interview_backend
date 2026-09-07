package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.dto.session.InterviewOptionResponse;
import com.baseProject.myBaseProject.dto.session.InterviewAnswerResponse;
import com.baseProject.myBaseProject.dto.session.InterviewConversationResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionOptionsResponse;
import com.baseProject.myBaseProject.dto.session.InterviewSessionStatusResponse;
import com.baseProject.myBaseProject.dto.session.InterviewTurnResponse;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewEndReason;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnAction;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.InterviewConversationService;
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

    @MockitoBean
    private InterviewConversationService conversationService;

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

    @Test
    void statusResponseDoesNotExposeInternalAiContext() throws Exception {
        when(service.get(7L, 501L)).thenReturn(response(InterviewSessionStatus.READY));

        mockMvc.perform(get("/api/interview-sessions/501").with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.jobContextSummary").doesNotExist())
                .andExpect(jsonPath("$.candidateContextSummary").doesNotExist())
                .andExpect(jsonPath("$.openingMessage").doesNotExist())
                .andExpect(jsonPath("$.focusAreas").doesNotExist());
    }

    @Test
    void userCanStartPreparedInterview() throws Exception {
        when(conversationService.start(7L, 501L)).thenReturn(conversationResponse());

        mockMvc.perform(post("/api/interview-sessions/501/start")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.deadlineAt").value("2026-09-06T08:30:00Z"))
                .andExpect(jsonPath("$.turns[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.turns[0].action").value("OPENING"));
    }

    @Test
    void userCanSubmitAnswerForCurrentTurn() throws Exception {
        InterviewTurnResponse candidate = new InterviewTurnResponse(
                2L, 1, InterviewTurnRole.CANDIDATE, "Tôi xây dựng REST API.",
                null, null, "answer-1",
                com.baseProject.myBaseProject.enums.InterviewTurnProcessingStatus.COMPLETED,
                null, NOW.plusSeconds(20));
        InterviewTurnResponse interviewer = new InterviewTurnResponse(
                3L, 2, InterviewTurnRole.INTERVIEWER,
                "Bạn đã xử lý lỗi API đó như thế nào?",
                InterviewTurnAction.FOLLOW_UP, "BACKEND",
                null, null, null, NOW.plusSeconds(21));
        when(conversationService.answer(eq(7L), eq(501L), eq("answer-1"), any()))
                .thenReturn(new InterviewAnswerResponse(
                        501L,
                        InterviewSessionStatus.IN_PROGRESS,
                        NOW.plusSeconds(1800),
                        null,
                        null,
                        1780,
                        2,
                        candidate,
                        interviewer));

        mockMvc.perform(post("/api/interview-sessions/501/answers")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "answer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedTurnIndex": 0,
                                  "answer": "Tôi xây dựng REST API."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTurnIndex").value(2))
                .andExpect(jsonPath("$.candidateTurn.role").value("CANDIDATE"))
                .andExpect(jsonPath("$.interviewerTurn.action").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.interviewerTurn.focusAreaCode").value("BACKEND"));
    }

    @Test
    void finishReturnsReasonForFrontendCompletionScreen() throws Exception {
        InterviewConversationResponse response = new InterviewConversationResponse(
                501L,
                InterviewSessionStatus.SCORING,
                NOW,
                NOW.plusSeconds(1800),
                InterviewEndReason.CANDIDATE_FINISHED,
                NOW.plusSeconds(600),
                0,
                0,
                conversationResponse().turns());
        when(conversationService.finish(7L, 501L)).thenReturn(response);

        mockMvc.perform(post("/api/interview-sessions/501/finish")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCORING"))
                .andExpect(jsonPath("$.endReason").value("CANDIDATE_FINISHED"))
                .andExpect(jsonPath("$.endedAt").value("2026-09-06T08:10:00Z"))
                .andExpect(jsonPath("$.remainingSeconds").value(0))
                .andExpect(jsonPath("$.turns.length()").value(1));
    }

    @Test
    void invalidAnswerDoesNotReachConversationService() throws Exception {
        mockMvc.perform(post("/api/interview-sessions/501/answers")
                        .with(user(userDetails()))
                        .header("Idempotency-Key", "answer-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answer": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(conversationService);
    }

    private InterviewSessionStatusResponse response(InterviewSessionStatus status) {
        return new InterviewSessionStatusResponse(
                501L, status, "Backend Java", "Minh profile", "vi", 30,
                InterviewerStyle.PROFESSIONAL, null, null, NOW, null, null);
    }

    private InterviewConversationResponse conversationResponse() {
        InterviewTurnResponse opening = new InterviewTurnResponse(
                1L,
                0,
                InterviewTurnRole.INTERVIEWER,
                "Xin chào, bạn hãy giới thiệu về mình.",
                InterviewTurnAction.OPENING,
                null,
                null,
                null,
                null,
                NOW);
        return new InterviewConversationResponse(
                501L,
                InterviewSessionStatus.IN_PROGRESS,
                NOW,
                NOW.plusSeconds(1800),
                null,
                null,
                1800,
                0,
                List.of(opening));
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
