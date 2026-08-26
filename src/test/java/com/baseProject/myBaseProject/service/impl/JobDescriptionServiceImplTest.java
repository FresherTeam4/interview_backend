package com.baseProject.myBaseProject.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.dto.common.PageResponse;
import com.baseProject.myBaseProject.dto.jd.CreateTextJobDescriptionRequest;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionResponse;
import com.baseProject.myBaseProject.dto.jd.JobDescriptionSummaryResponse;
import com.baseProject.myBaseProject.dto.jd.UpdateJobDescriptionRequest;
import com.baseProject.myBaseProject.entity.JobDescription;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.JobDescriptionSourceType;
import com.baseProject.myBaseProject.enums.JobDescriptionStatus;
import com.baseProject.myBaseProject.exception.JobDescriptionAlreadyConfirmedException;
import com.baseProject.myBaseProject.exception.JobDescriptionContentRequiredException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidTextException;
import com.baseProject.myBaseProject.exception.JobDescriptionLimitReachedException;
import com.baseProject.myBaseProject.exception.JobDescriptionNotFoundException;
import com.baseProject.myBaseProject.jd.JobDescriptionFileProcessor;
import com.baseProject.myBaseProject.mapper.JobDescriptionMapper;
import com.baseProject.myBaseProject.repository.JobDescriptionRepository;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.storage.FileStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class JobDescriptionServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long JD_ID = 12L;
    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final String VALID_TEXT =
            "Build and maintain Java backend services for the interview platform.";

    @Mock
    private JobDescriptionRepository jobDescriptionRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private JobDescriptionFileProcessor fileProcessor;
    @Mock
    private FileStorageService fileStorage;
    @Mock
    private JobDescriptionFilePersistenceService filePersistenceService;

    private JobDescriptionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobDescriptionServiceImpl(
                jobDescriptionRepository,
                userAccountRepository,
                new JobDescriptionMapper(),
                new JobDescriptionProperties(10, 200, 3, 2_097_152, 20),
                fileProcessor,
                fileStorage,
                filePersistenceService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createTextLocksUserChecksCapacityAndPersistsDraft() {
        UserAccount user = UserAccount.builder().id(USER_ID).build();
        when(userAccountRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(jobDescriptionRepository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(2L);
        when(jobDescriptionRepository.save(any(JobDescription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobDescriptionResponse response = service.createText(
                USER_ID,
                new CreateTextJobDescriptionRequest("  Java Backend Engineer  ", VALID_TEXT));

        ArgumentCaptor<JobDescription> captor = ArgumentCaptor.forClass(JobDescription.class);
        verify(jobDescriptionRepository).save(captor.capture());
        JobDescription saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Java Backend Engineer");
        assertThat(saved.getSourceType()).isEqualTo(JobDescriptionSourceType.TEXT);
        assertThat(saved.getStatus()).isEqualTo(JobDescriptionStatus.DRAFT);
        assertThat(saved.getRawText()).isEqualTo(VALID_TEXT);
        assertThat(saved.getConfirmedText()).isEqualTo(VALID_TEXT);
        assertThat(saved.getChecksumSha256()).matches("[0-9a-f]{64}");
        assertThat(saved.getOriginalFilename()).isNull();
        assertThat(saved.getStorageKey()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.rawText()).isEqualTo(VALID_TEXT);

        InOrder order = inOrder(userAccountRepository, jobDescriptionRepository);
        order.verify(userAccountRepository).findByIdForUpdate(USER_ID);
        order.verify(jobDescriptionRepository).countByUserIdAndActiveTrue(USER_ID);
        order.verify(jobDescriptionRepository).save(any(JobDescription.class));
    }

    @Test
    void createTextRejectsBlankContentBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.createText(
                USER_ID,
                new CreateTextJobDescriptionRequest("Title", "   \r\n ")))
                .isInstanceOf(JobDescriptionContentRequiredException.class);

        verifyNoInteractions(userAccountRepository, jobDescriptionRepository);
    }

    @Test
    void createTextRejectsContentOutsideConfiguredRange() {
        assertThatThrownBy(() -> service.createText(
                USER_ID,
                new CreateTextJobDescriptionRequest("Title", "too short")))
                .isInstanceOf(JobDescriptionInvalidTextException.class);

        verifyNoInteractions(userAccountRepository, jobDescriptionRepository);
    }

    @Test
    void createTextRejectsWhenActiveLimitIsReached() {
        UserAccount user = UserAccount.builder().id(USER_ID).build();
        when(userAccountRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(jobDescriptionRepository.countByUserIdAndActiveTrue(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.createText(
                USER_ID,
                new CreateTextJobDescriptionRequest("Title", VALID_TEXT)))
                .isInstanceOf(JobDescriptionLimitReachedException.class);

        verify(jobDescriptionRepository, never()).save(any());
    }

    @Test
    void listReturnsSummaryPageWithoutFullTextFields() {
        JobDescription first = draft(JD_ID, "First", "raw secret");
        Pageable expectedPageable = PageRequest.of(0, 20);
        when(jobDescriptionRepository.findByUserIdAndActiveTrue(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first), expectedPageable, 1));

        PageResponse<JobDescriptionSummaryResponse> result = service.list(USER_ID, 0, 20);

        assertThat(result.items()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(JD_ID);
            assertThat(summary.title()).isEqualTo("First");
            assertThat(summary.sourceType()).isEqualTo(JobDescriptionSourceType.TEXT);
        });
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(JobDescriptionSummaryResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("rawText", "confirmedText");
    }

    @Test
    void getUsesOwnershipScopedActiveQuery() {
        when(jobDescriptionRepository.findByIdAndUserIdAndActiveTrue(JD_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, JD_ID))
                .isInstanceOf(JobDescriptionNotFoundException.class);
    }

    @Test
    void updateChangesOnlyDraftFieldsAndPreservesRawText() {
        JobDescription jobDescription = draft(JD_ID, "Old title", "immutable raw text");
        when(jobDescriptionRepository.findActiveOwnedByIdForUpdate(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));

        JobDescriptionResponse response = service.update(
                USER_ID,
                JD_ID,
                new UpdateJobDescriptionRequest("  New title  ", VALID_TEXT));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.rawText()).isEqualTo("immutable raw text");
        assertThat(response.confirmedText()).isEqualTo(VALID_TEXT);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(JobDescriptionStatus.DRAFT);
    }

    @Test
    void updateRejectsConfirmedJobDescription() {
        JobDescription jobDescription = ready(JD_ID);
        when(jobDescriptionRepository.findActiveOwnedByIdForUpdate(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));

        assertThatThrownBy(() -> service.update(
                USER_ID,
                JD_ID,
                new UpdateJobDescriptionRequest("New title", VALID_TEXT)))
                .isInstanceOf(JobDescriptionAlreadyConfirmedException.class);
    }

    @Test
    void confirmIsIdempotentAndPreservesFirstConfirmationTimestamp() {
        Instant firstConfirmation = Instant.parse("2026-08-25T08:00:00Z");
        Instant firstUpdate = Instant.parse("2026-08-25T08:00:00Z");
        JobDescription jobDescription = JobDescription.builder()
                .id(JD_ID)
                .sourceType(JobDescriptionSourceType.TEXT)
                .status(JobDescriptionStatus.READY)
                .checksumSha256("a".repeat(64))
                .rawText(VALID_TEXT)
                .confirmedText(VALID_TEXT)
                .confirmedAt(firstConfirmation)
                .active(true)
                .createdAt(firstUpdate)
                .updatedAt(firstUpdate)
                .build();
        when(jobDescriptionRepository.findActiveOwnedByIdForUpdate(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));

        JobDescriptionResponse response = service.confirm(USER_ID, JD_ID);

        assertThat(response.confirmedAt()).isEqualTo(firstConfirmation);
        assertThat(response.updatedAt()).isEqualTo(firstUpdate);
    }

    @Test
    void confirmDraftSetsReadyAndUsesClock() {
        JobDescription jobDescription = draft(JD_ID, "Title", VALID_TEXT);
        when(jobDescriptionRepository.findActiveOwnedByIdForUpdate(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));

        JobDescriptionResponse response = service.confirm(USER_ID, JD_ID);

        assertThat(response.status()).isEqualTo(JobDescriptionStatus.READY);
        assertThat(response.confirmedAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteSoftDeletesOwnedActiveJobDescription() {
        JobDescription jobDescription = draft(JD_ID, "Title", VALID_TEXT);
        when(jobDescriptionRepository.findActiveOwnedByIdForUpdate(JD_ID, USER_ID))
                .thenReturn(Optional.of(jobDescription));

        service.delete(USER_ID, JD_ID);

        assertThat(jobDescription.isActive()).isFalse();
        assertThat(jobDescription.getUpdatedAt()).isEqualTo(NOW);
        verify(jobDescriptionRepository, never()).delete(any());
    }

    private static JobDescription draft(Long id, String title, String rawText) {
        Instant createdAt = Instant.parse("2026-08-25T01:00:00Z");
        return JobDescription.builder()
                .id(id)
                .title(title)
                .sourceType(JobDescriptionSourceType.TEXT)
                .status(JobDescriptionStatus.DRAFT)
                .checksumSha256("a".repeat(64))
                .rawText(rawText)
                .confirmedText(rawText)
                .active(true)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private static JobDescription ready(Long id) {
        Instant confirmedAt = Instant.parse("2026-08-25T02:00:00Z");
        return JobDescription.builder()
                .id(id)
                .title("Ready JD")
                .sourceType(JobDescriptionSourceType.TEXT)
                .status(JobDescriptionStatus.READY)
                .checksumSha256("a".repeat(64))
                .rawText(VALID_TEXT)
                .confirmedText(VALID_TEXT)
                .confirmedAt(confirmedAt)
                .active(true)
                .createdAt(confirmedAt)
                .updatedAt(confirmedAt)
                .build();
    }
}
