package com.baseProject.myBaseProject.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.JobDescriptionLimitReachedException;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class JobDescriptionFilePersistenceServiceTest {

    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");

    @Mock
    private JobDescriptionRepository jobDescriptionRepository;
    @Mock
    private UserAccountRepository userAccountRepository;

    private JobDescriptionFilePersistenceService service;

    @BeforeEach
    void setUp() {
        service = new JobDescriptionFilePersistenceService(
                jobDescriptionRepository,
                userAccountRepository,
                new JobDescriptionProperties(10, 200, 3, 2_097_152, 20));
    }

    @Test
    void persistLocksUserChecksQuotaAndStoresFileMetadata() {
        UserAccount user = UserAccount.builder().id(USER_ID).build();
        when(userAccountRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(jobDescriptionRepository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(2L);
        when(jobDescriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        JobDescriptionFilePersistenceService.FileDraft draft = draft();

        JobDescription result = service.persist(USER_ID, draft);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getSourceType()).isEqualTo(JobDescriptionSourceType.FILE);
        assertThat(result.getStatus()).isEqualTo(JobDescriptionStatus.DRAFT);
        assertThat(result.getOriginalFilename()).isEqualTo("backend.pdf");
        assertThat(result.getStorageKey()).isEqualTo("jd/7/file.pdf");
        assertThat(result.getFileSizeBytes()).isEqualTo(1_234L);
        assertThat(result.getRawText()).isEqualTo(draft.text());
        assertThat(result.getConfirmedText()).isEqualTo(draft.text());
        assertThat(result.getCreatedAt()).isEqualTo(NOW);

        InOrder order = inOrder(userAccountRepository, jobDescriptionRepository);
        order.verify(userAccountRepository).findByIdForUpdate(USER_ID);
        order.verify(jobDescriptionRepository).countByUserIdAndActiveTrue(USER_ID);
        order.verify(jobDescriptionRepository).save(any());
    }

    @Test
    void persistRejectsAtQuotaWhileHoldingUserLock() {
        UserAccount user = UserAccount.builder().id(USER_ID).build();
        when(userAccountRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(jobDescriptionRepository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.persist(USER_ID, draft()))
                .isInstanceOf(JobDescriptionLimitReachedException.class);

        verify(jobDescriptionRepository, never()).save(any());
    }

    private static JobDescriptionFilePersistenceService.FileDraft draft() {
        return new JobDescriptionFilePersistenceService.FileDraft(
                "Backend Engineer",
                "backend.pdf",
                "jd/7/file.pdf",
                "application/pdf",
                1_234,
                "a".repeat(64),
                "Build and maintain Java backend services.",
                NOW);
    }
}
