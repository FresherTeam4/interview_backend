package com.baseProject.myBaseProject.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.jd.CreateTextJobDescriptionRequest;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionFileUrlResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionSummaryResponse;
import com.baseProject.myBaseProject.dto.jd.UpdateJobDescriptionRequest;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.JobDescriptionAlreadyConfirmedException;
import com.baseProject.myBaseProject.exception.JobDescriptionHasNoFileException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidFileTypeException;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.security.CustomUserDetailsService;
import com.baseProject.myBaseProject.service.JwtService;
import com.baseProject.myBaseProject.service.JobDescriptionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(JobDescriptionController.class)
@Import(JobDescriptionControllerTest.MethodSecurityTestConfig.class)
class JobDescriptionControllerTest {

    private static final Long USER_ID = 7L;
    private static final Long JD_ID = 12L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-26T07:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobDescriptionService jobDescriptionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void createTextReturnsCreatedResourceAndLocation() throws Exception {
        CreateTextJobDescriptionRequest request = new CreateTextJobDescriptionRequest(
                "Fresher Java Developer",
                "Responsibilities and requirements for a Java backend engineering position.");
        when(jobDescriptionService.createText(USER_ID, request)).thenReturn(fullResponse());

        mockMvc.perform(post("/api/job-descriptions/text")
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/job-descriptions/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.sourceType").value("TEXT"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rawText").value(fullResponse().rawText()));

        verify(jobDescriptionService).createText(USER_ID, request);
    }

    @Test
    void createFileReturnsCreatedDraftAndLocation() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "backend.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-test".getBytes(StandardCharsets.UTF_8));
        MockPart title = new MockPart(
                "title", "Backend Engineer".getBytes(StandardCharsets.UTF_8));
        title.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        when(jobDescriptionService.createFile(USER_ID, "Backend Engineer", file))
                .thenReturn(fileResponse());

        mockMvc.perform(multipart("/api/job-descriptions/file")
                        .file(file)
                        .part(title)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/job-descriptions/12"))
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.originalFilename").value("backend.pdf"))
                .andExpect(jsonPath("$.rawText").value(fileResponse().rawText()));

        verify(jobDescriptionService).createFile(USER_ID, "Backend Engineer", file);
    }

    @Test
    void createFileReturnsFeatureErrorContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "backend.docx", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1});
        when(jobDescriptionService.createFile(USER_ID, null, file))
                .thenThrow(new JobDescriptionInvalidFileTypeException());

        mockMvc.perform(multipart("/api/job-descriptions/file")
                        .file(file)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("JD_INVALID_FILE_TYPE"));
    }

    @Test
    void createFileRejectsTitleAboveContractLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "backend.txt", MediaType.TEXT_PLAIN_VALUE,
                "valid".getBytes(StandardCharsets.UTF_8));
        MockPart title = new MockPart(
                "title", "a".repeat(201).getBytes(StandardCharsets.UTF_8));
        title.getHeaders().setContentType(MediaType.TEXT_PLAIN);

        mockMvc.perform(multipart("/api/job-descriptions/file")
                        .file(file)
                        .part(title)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(jobDescriptionService);
    }

    @Test
    void listReturnsPageOfSummariesWithoutFullText() throws Exception {
        JobDescriptionSummaryResponse summary = new JobDescriptionSummaryResponse(
                JD_ID,
                "Fresher Java Developer",
                JobDescriptionSourceType.TEXT,
                JobDescriptionStatus.DRAFT,
                null,
                null,
                CREATED_AT,
                CREATED_AT);
        when(jobDescriptionService.list(USER_ID, 0, 20))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1));

        mockMvc.perform(get("/api/job-descriptions")
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(12))
                .andExpect(jsonPath("$.items[0].title").value("Fresher Java Developer"))
                .andExpect(jsonPath("$.items[0].rawText").doesNotExist())
                .andExpect(jsonPath("$.items[0].confirmedText").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(jobDescriptionService).list(USER_ID, 0, 20);
    }

    @Test
    void getReturnsFullOwnedResource() throws Exception {
        when(jobDescriptionService.get(USER_ID, JD_ID)).thenReturn(fullResponse());

        mockMvc.perform(get("/api/job-descriptions/{id}", JD_ID)
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.confirmedText").value(fullResponse().confirmedText()));

        verify(jobDescriptionService).get(USER_ID, JD_ID);
    }

    @Test
    void fileUrlReturnsShortLivedPresignedUrl() throws Exception {
        Instant expiresAt = Instant.parse("2026-08-26T07:35:00Z");
        when(jobDescriptionService.fileUrl(USER_ID, JD_ID))
                .thenReturn(new JobDescriptionFileUrlResponse(
                        "https://storage.example/jd", expiresAt));

        mockMvc.perform(get("/api/job-descriptions/{id}/file", JD_ID)
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://storage.example/jd"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-26T07:35:00Z"));

        verify(jobDescriptionService).fileUrl(USER_ID, JD_ID);
    }

    @Test
    void fileUrlReturnsConflictForTextSource() throws Exception {
        when(jobDescriptionService.fileUrl(USER_ID, JD_ID))
                .thenThrow(new JobDescriptionHasNoFileException());

        mockMvc.perform(get("/api/job-descriptions/{id}/file", JD_ID)
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JD_HAS_NO_FILE"));
    }

    @Test
    void updateReturnsConflictWhenJobDescriptionIsConfirmed() throws Exception {
        UpdateJobDescriptionRequest request = new UpdateJobDescriptionRequest(
                "Updated title",
                "Updated responsibilities and requirements for this backend engineering role.");
        when(jobDescriptionService.update(USER_ID, JD_ID, request))
                .thenThrow(new JobDescriptionAlreadyConfirmedException());

        mockMvc.perform(put("/api/job-descriptions/{id}", JD_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JD_ALREADY_CONFIRMED"));
    }

    @Test
    void updateReturnsUpdatedDraft() throws Exception {
        UpdateJobDescriptionRequest request = new UpdateJobDescriptionRequest(
                "Updated title",
                "Updated responsibilities and requirements for this backend engineering role.");
        JobDescriptionResponse response = new JobDescriptionResponse(
                JD_ID,
                request.title(),
                JobDescriptionSourceType.TEXT,
                JobDescriptionStatus.DRAFT,
                null,
                fullResponse().rawText(),
                request.confirmedText(),
                null,
                CREATED_AT,
                CREATED_AT);
        when(jobDescriptionService.update(USER_ID, JD_ID, request)).thenReturn(response);

        mockMvc.perform(put("/api/job-descriptions/{id}", JD_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.confirmedText").value(request.confirmedText()));

        verify(jobDescriptionService).update(USER_ID, JD_ID, request);
    }

    @Test
    void confirmReturnsReadyResource() throws Exception {
        JobDescriptionResponse ready = new JobDescriptionResponse(
                JD_ID,
                fullResponse().title(),
                JobDescriptionSourceType.TEXT,
                JobDescriptionStatus.READY,
                null,
                fullResponse().rawText(),
                fullResponse().confirmedText(),
                CREATED_AT,
                CREATED_AT,
                CREATED_AT);
        when(jobDescriptionService.confirm(USER_ID, JD_ID)).thenReturn(ready);

        mockMvc.perform(post("/api/job-descriptions/{id}/confirm", JD_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.confirmedAt").value("2026-08-26T07:30:00Z"));

        verify(jobDescriptionService).confirm(USER_ID, JD_ID);
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/job-descriptions/{id}", JD_ID)
                        .with(user(userDetails(UserRole.USER)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(jobDescriptionService).delete(USER_ID, JD_ID);
    }

    @Test
    void invalidPageSizeReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/job-descriptions?size=51")
                        .with(user(userDetails(UserRole.USER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.size").value("size tối đa là 50"));

        verifyNoInteractions(jobDescriptionService);
    }

    @Test
    void adminRoleCannotUseUserJobDescriptionEndpoints() throws Exception {
        mockMvc.perform(get("/api/job-descriptions")
                        .with(user(userDetails(UserRole.ADMIN))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(jobDescriptionService);
    }

    private static JobDescriptionResponse fullResponse() {
        String text = "Responsibilities and requirements for a Java backend engineering position.";
        return new JobDescriptionResponse(
                JD_ID,
                "Fresher Java Developer",
                JobDescriptionSourceType.TEXT,
                JobDescriptionStatus.DRAFT,
                null,
                text,
                text,
                null,
                CREATED_AT,
                CREATED_AT);
    }

    private static JobDescriptionResponse fileResponse() {
        String text = "Responsibilities and requirements for a Java backend engineering position.";
        return new JobDescriptionResponse(
                JD_ID,
                "Backend Engineer",
                JobDescriptionSourceType.FILE,
                JobDescriptionStatus.DRAFT,
                "backend.pdf",
                text,
                text,
                null,
                CREATED_AT,
                CREATED_AT);
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
