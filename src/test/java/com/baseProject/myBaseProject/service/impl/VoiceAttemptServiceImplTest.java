package com.baseProject.myBaseProject.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptUploadRequest;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.exception.StorageUnavailableException;
import com.baseProject.myBaseProject.exception.VoiceAttemptNotFoundException;
import com.baseProject.myBaseProject.interview.voice.AudioRecordingProcessor;
import com.baseProject.myBaseProject.interview.voice.AudioRecordingProcessor.ProcessedAudio;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore.PersistResult;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore.VoiceAttemptDraft;
import com.baseProject.myBaseProject.mapper.VoiceAttemptMapper;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;
import com.baseProject.myBaseProject.storage.FileStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class VoiceAttemptServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 42L;
    private static final Long ATTEMPT_ID = 301L;
    private static final byte[] AUDIO = {1, 2, 3};
    private static final VoiceAttemptUploadRequest REQUEST = new VoiceAttemptUploadRequest(
            205L,
            "attempt-1",
            5_000,
            9L);

    @Mock
    private AudioRecordingProcessor audioProcessor;
    @Mock
    private VoiceAttemptStore attemptStore;
    @Mock
    private VoiceAnswerAttemptRepository attemptRepository;
    @Mock
    private VoiceAttemptMapper attemptMapper;
    @Mock
    private FileStorageService fileStorage;
    @Mock
    private VoiceAnswerAttempt attempt;

    private MockMultipartFile file;
    private ProcessedAudio processed;
    private VoiceAttemptResponse response;
    private VoiceAttemptServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoiceAttemptServiceImpl(
                audioProcessor,
                attemptStore,
                attemptRepository,
                attemptMapper,
                fileStorage);
        file = new MockMultipartFile("file", "voice.webm", "audio/webm", AUDIO);
        processed = new ProcessedAudio(
                AudioFormat.WEBM_OPUS,
                AUDIO,
                5_000,
                "a".repeat(64));
        response = new VoiceAttemptResponse(
                ATTEMPT_ID,
                205L,
                (short) 1,
                VoiceAttemptStatus.RECORDED,
                0,
                null,
                null,
                5_000,
                null,
                Instant.parse("2026-08-26T08:00:00Z"));
    }

    @Test
    void exactReplayDoesNotUploadObjectAgain() {
        when(audioProcessor.process(file, 5_000)).thenReturn(processed);
        when(attemptStore.findReplayOrValidate(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(Optional.of(attempt));
        when(attemptMapper.toResponse(attempt)).thenReturn(response);

        assertThat(service.upload(USER_ID, SESSION_ID, REQUEST, file)).isSameAs(response);

        verify(fileStorage, never()).upload(any(), any(), any());
        verify(attemptStore, never()).persist(any(), any(), any());
    }

    @Test
    void newRecordingUploadsUnderScopedKeyAndPersistsMetadata() {
        when(audioProcessor.process(file, 5_000)).thenReturn(processed);
        when(attemptStore.findReplayOrValidate(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(Optional.empty());
        when(attemptStore.persist(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(new PersistResult(attempt, true));
        when(attemptMapper.toResponse(attempt)).thenReturn(response);

        assertThat(service.upload(USER_ID, SESSION_ID, REQUEST, file)).isSameAs(response);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).upload(
                key.capture(),
                org.mockito.ArgumentMatchers.same(AUDIO),
                org.mockito.ArgumentMatchers.eq("audio/webm"));
        assertThat(key.getValue())
                .startsWith("interview-audio/7/42/answers/")
                .endsWith(".webm");
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void persistenceFailureDeletesUploadedObjectAsCompensation() {
        when(audioProcessor.process(file, 5_000)).thenReturn(processed);
        when(attemptStore.findReplayOrValidate(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(Optional.empty());
        when(attemptStore.persist(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.upload(USER_ID, SESSION_ID, REQUEST, file))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).upload(
                key.capture(),
                org.mockito.ArgumentMatchers.same(AUDIO),
                org.mockito.ArgumentMatchers.eq("audio/webm"));
        verify(fileStorage).delete(key.getValue());
    }

    @Test
    void storageFailureIsPropagatedWithoutWritingDatabase() {
        when(audioProcessor.process(file, 5_000)).thenReturn(processed);
        when(attemptStore.findReplayOrValidate(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(Optional.empty());
        doThrow(new StorageUnavailableException(
                        new IllegalStateException("storage unavailable")))
                .when(fileStorage)
                .upload(any(), any(), any());

        assertThatThrownBy(() -> service.upload(USER_ID, SESSION_ID, REQUEST, file))
                .isInstanceOf(StorageUnavailableException.class);

        verify(attemptStore, never()).persist(any(), any(), any());
        verify(fileStorage, never()).delete(any());
    }

    @Test
    void concurrentIdempotentWinnerCausesSecondObjectToBeDeleted() {
        when(audioProcessor.process(file, 5_000)).thenReturn(processed);
        when(attemptStore.findReplayOrValidate(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(Optional.empty());
        when(attemptStore.persist(
                        org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(SESSION_ID),
                        any(VoiceAttemptDraft.class)))
                .thenReturn(new PersistResult(attempt, false));
        when(attemptMapper.toResponse(attempt)).thenReturn(response);

        assertThat(service.upload(USER_ID, SESSION_ID, REQUEST, file)).isSameAs(response);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).upload(
                key.capture(),
                org.mockito.ArgumentMatchers.same(AUDIO),
                org.mockito.ArgumentMatchers.eq("audio/webm"));
        verify(fileStorage).delete(key.getValue());
    }

    @Test
    void getHidesAttemptsOutsideOwnedSession() {
        when(attemptRepository.findByIdAndSessionIdAndSessionUserId(
                        ATTEMPT_ID,
                        SESSION_ID,
                        USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, SESSION_ID, ATTEMPT_ID))
                .isInstanceOf(VoiceAttemptNotFoundException.class);
    }
}
