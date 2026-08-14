package com.baseProject.myBaseProject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.baseProject.myBaseProject.dto.question.QuestionCreateRequest;
import com.baseProject.myBaseProject.dto.question.QuestionResponse;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.QuestionDifficulty;
import com.baseProject.myBaseProject.enums.QuestionLevel;
import com.baseProject.myBaseProject.enums.QuestionType;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionService questionService;

    @Test
    void anonymousUserReceivesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/questions/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(questionService);
    }

    @Test
    void participantReceivesForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/questions/1").with(user(userDetails(UserRole.PARTICIPANT))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(questionService);
    }

    @Test
    void eventAdminCanReadQuestion() throws Exception {
        when(questionService.getById(1L)).thenReturn(response(1L));

        mockMvc.perform(get("/api/admin/questions/1").with(user(userDetails(UserRole.EVENT_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.contentVi").value("Dependency Injection là gì?"))
                .andExpect(jsonPath("$.version").value(0));

        verify(questionService).getById(1L);
    }

    @Test
    void createReturnsLocationAndAuthenticatedCreator() throws Exception {
        when(questionService.create(any(QuestionCreateRequest.class), eq(10L)))
                .thenReturn(response(101L));

        mockMvc.perform(post("/api/admin/questions")
                        .with(user(userDetails(UserRole.EVENT_ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentVi": "Dependency Injection là gì?",
                                  "contentEn": "What is Dependency Injection?",
                                  "techStackId": 2,
                                  "level": "JUNIOR",
                                  "questionType": "TECHNICAL",
                                  "difficulty": "MEDIUM",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/admin/questions/101"))
                .andExpect(jsonPath("$.id").value(101));

        verify(questionService).create(any(QuestionCreateRequest.class), eq(10L));
    }

    @Test
    void invalidCreateRequestReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/admin/questions")
                        .with(user(userDetails(UserRole.EVENT_ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentVi": " ",
                                  "level": null,
                                  "questionType": null,
                                  "difficulty": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.contentVi").exists())
                .andExpect(jsonPath("$.fieldErrors.level").exists())
                .andExpect(jsonPath("$.fieldErrors.questionType").exists())
                .andExpect(jsonPath("$.fieldErrors.difficulty").exists());

        verifyNoInteractions(questionService);
    }

    private static CustomUserDetails userDetails(UserRole role) {
        return new CustomUserDetails(UserAccount.builder()
                .id(10L)
                .email("admin@example.com")
                .passwordHash("password")
                .role(role)
                .enabled(true)
                .build());
    }

    private static QuestionResponse response(Long id) {
        return new QuestionResponse(
                id,
                "Dependency Injection là gì?",
                "What is Dependency Injection?",
                null,
                QuestionLevel.JUNIOR,
                QuestionType.TECHNICAL,
                QuestionDifficulty.MEDIUM,
                null,
                10L,
                true,
                0,
                Instant.parse("2026-08-12T00:00:00Z"),
                Instant.parse("2026-08-12T00:00:00Z")
        );
    }
}
