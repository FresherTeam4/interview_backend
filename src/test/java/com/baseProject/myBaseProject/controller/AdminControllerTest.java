package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.dto.admin.AdminOverviewResponse;
import com.baseProject.myBaseProject.dto.admin.AdminPageResponse;
import com.baseProject.myBaseProject.dto.admin.AdminSessionDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserDetailResponse;
import com.baseProject.myBaseProject.dto.admin.AdminUserSummaryResponse;
import com.baseProject.myBaseProject.dto.admin.UpdateUserStatusRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.InterviewSessionMode;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewerStyle;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.AdminInterviewSessionService;
import com.baseProject.myBaseProject.service.AdminOverviewService;
import com.baseProject.myBaseProject.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOverviewService overviewService;

    @MockitoBean
    private AdminUserService userService;

    @MockitoBean
    private AdminInterviewSessionService sessionService;

    @Test
    void anonymousCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(overviewService);
    }

    @Test
    void regularUserCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/overview").with(user(userDetails(UserRole.USER))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(overviewService);
    }

    @Test
    void adminCanReadOverview() throws Exception {
        when(overviewService.get(7)).thenReturn(new AdminOverviewResponse(
                NOW,
                7,
                new AdminOverviewResponse.UserMetrics(10, 9, 2),
                new AdminOverviewResponse.SessionMetrics(
                        8, 5, 1, 1, 1, new BigDecimal("62.50")),
                new AdminOverviewResponse.TemplateMetrics(3)));

        mockMvc.perform(get("/api/admin/overview").with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(10))
                .andExpect(jsonPath("$.sessions.completionRate").value(62.5))
                .andExpect(jsonPath("$.templates.published").value(3));
    }

    @Test
    void adminCanFilterUsers() throws Exception {
        when(userService.list("minh", UserRole.USER, true, 0, 20))
                .thenReturn(new AdminPageResponse<>(List.of(new AdminUserSummaryResponse(
                        15L, "Minh", "minh@example.com", UserRole.USER,
                        true, NOW, NOW)), 0, 20, 1));

        mockMvc.perform(get("/api/admin/users")
                        .with(user(userDetails(UserRole.ADMIN)))
                        .param("keyword", "minh")
                        .param("role", "USER")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].email").value("minh@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminCanDisableUser() throws Exception {
        AdminUserDetailResponse response = new AdminUserDetailResponse(
                15L, "Minh", "minh@example.com", null, UserRole.USER,
                false, NOW, NOW, new AdminUserDetailResponse.Activity(1, 1, 2, 1));
        when(userService.updateStatus(
                eq(3L), eq(15L), eq(new UpdateUserStatusRequest(false))))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/users/15/status")
                        .with(user(userDetails(UserRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void missingUserStatusIsRejectedBeforeService() throws Exception {
        mockMvc.perform(patch("/api/admin/users/15/status")
                        .with(user(userDetails(UserRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(userService);
    }

    @Test
    void adminCanRetryFailedPreparation() throws Exception {
        when(sessionService.retryPreparation(3L, 501L))
                .thenReturn(sessionDetail(InterviewSessionStatus.PREPARING));

        mockMvc.perform(post("/api/admin/interview-sessions/501/preparation/retry")
                        .with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.status").value("PREPARING"));
    }

    private AdminSessionDetailResponse sessionDetail(InterviewSessionStatus status) {
        return new AdminSessionDetailResponse(
                501L,
                new com.baseProject.myBaseProject.dto.admin.AdminUserReferenceResponse(
                        7L, "Minh", "minh@example.com"),
                status,
                "Backend Java",
                "Minh profile",
                "vi",
                30,
                InterviewerStyle.PROFESSIONAL,
                InterviewSessionMode.TURN_BASED,
                null,
                null,
                0,
                null,
                null,
                "v1",
                "test-model",
                "v3",
                null,
                null,
                NOW,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                List.of());
    }

    private CustomUserDetails userDetails(UserRole role) {
        return new CustomUserDetails(UserAccount.builder()
                .id(role == UserRole.ADMIN ? 3L : 7L)
                .email(role == UserRole.ADMIN
                        ? "admin@example.com" : "user@example.com")
                .passwordHash("password")
                .role(role)
                .enabled(true)
                .build());
    }
}
