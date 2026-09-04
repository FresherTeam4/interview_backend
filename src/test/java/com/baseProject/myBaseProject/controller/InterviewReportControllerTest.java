package com.baseProject.myBaseProject.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseProject.myBaseProject.dto.report.InterviewReportResponse;
import com.baseProject.myBaseProject.dto.report.InterviewReportResponse.CriterionScore;
import com.baseProject.myBaseProject.dto.report.InterviewReportResponse.Evidence;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.ReportResultStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.interview.scoring.InterviewScoringStore;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.CustomUserDetailsService;
import com.baseProject.myBaseProject.service.JwtService;
import com.baseProject.myBaseProject.service.InterviewReportService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@WebMvcTest(InterviewReportController.class)
@Import(InterviewReportControllerTest.MethodSecurityTestConfig.class)
class InterviewReportControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final Instant GENERATED_AT = Instant.parse("2026-08-26T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InterviewReportService reportService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void getReturnsNumericScoresAndVerifiedEvidence() throws Exception {
        when(reportService.get(USER_ID, SESSION_ID)).thenReturn(report());

        mockMvc.perform(get("/api/sessions/{sessionId}/report", SESSION_ID)
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("SCORED"))
                .andExpect(jsonPath("$.overallScore").value(75.0))
                .andExpect(jsonPath("$.partial").value(true))
                .andExpect(jsonPath("$.criteria[0].score").value(3.0))
                .andExpect(jsonPath("$.criteria[0].evidences[0].turnId").value(101))
                .andExpect(jsonPath("$.strengths[0]").value("Nền tảng tốt"));

        verify(reportService).get(USER_ID, SESSION_ID);
    }

    @Test
    void adminCannotReadUserReport() throws Exception {
        mockMvc.perform(get("/api/sessions/{sessionId}/report", SESSION_ID)
                        .with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reportService);
    }

    private InterviewReportResponse report() {
        return new InterviewReportResponse(
                SESSION_ID,
                ReportResultStatus.SCORED,
                new BigDecimal("75.00"),
                true,
                new BigDecimal("0.5000"),
                new BigDecimal("0.600"),
                "Tóm tắt",
                InterviewScoringStore.DISCLAIMER,
                List.of(new CriterionScore(
                        "TECHNICAL_DEPTH",
                        "Độ sâu kỹ thuật",
                        new BigDecimal("3.00"),
                        new BigDecimal("4.00"),
                        (short) 3,
                        "Nhận xét",
                        List.of(new Evidence(101L, "chọn Redis", 3, 13)))),
                List.of("Nền tảng tốt"),
                List.of("Cần thêm số liệu"),
                List.of("Luyện trade-off"),
                GENERATED_AT);
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
            return Clock.fixed(GENERATED_AT, ZoneOffset.UTC);
        }
    }
}
