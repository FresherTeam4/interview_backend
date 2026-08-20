package com.baseProject.myBaseProject.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseProject.myBaseProject.dto.question.imports.QuestionImportSummaryResponse;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.QuestionImportStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.QuestionImportAsyncWorker;
import com.baseProject.myBaseProject.service.QuestionImportService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionImportAdminControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuestionImportService importService;

    @MockitoBean
    private QuestionImportAsyncWorker asyncWorker;

    @Test
    void userCannotUploadQuestionCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.csv",
                "text/csv",
                "header".getBytes()
        );

        mockMvc.perform(multipart("/api/admin/question-imports")
                        .file(file)
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(importService, asyncWorker);
    }

    @Test
    void adminUploadsAndStartsValidation() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questions.csv",
                "text/csv",
                "header".getBytes()
        );
        when(importService.stage(file, 10L)).thenReturn(summary(
                QuestionImportStatus.UPLOADED
        ));

        mockMvc.perform(multipart("/api/admin/question-imports")
                        .file(file)
                        .with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "http://localhost/api/admin/question-imports/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        verify(importService).stage(file, 10L);
        verify(asyncWorker).validateAsync(7L);
    }

    @Test
    void adminCommitsValidatedRows() throws Exception {
        when(importService.prepareCommit(7L)).thenReturn(summary(
                QuestionImportStatus.IMPORTING
        ));

        mockMvc.perform(post("/api/admin/question-imports/7/commit")
                        .with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("IMPORTING"));

        verify(importService).prepareCommit(7L);
        verify(asyncWorker).importAsync(7L);
    }

    @Test
    void templateIsAnExcelFriendlyUtf8Csv() throws Exception {
        mockMvc.perform(get("/api/admin/question-imports/template")
                        .with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("question-import-template.csv")
                ))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(result -> assertArrayEquals(
                        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                        java.util.Arrays.copyOf(result.getResponse().getContentAsByteArray(), 3)
                ));
    }

    private static QuestionImportSummaryResponse summary(QuestionImportStatus status) {
        return new QuestionImportSummaryResponse(
                7L,
                "questions.csv",
                status,
                1,
                1,
                0,
                0,
                0,
                0,
                null,
                Instant.parse("2026-08-20T00:00:00Z"),
                null,
                null
        );
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
}
