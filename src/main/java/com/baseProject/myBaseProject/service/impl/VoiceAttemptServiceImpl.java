package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.interview.VoiceAttemptResponse;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptConfirmRequest;
import com.baseProject.myBaseProject.dto.interview.VoiceAttemptUploadRequest;
import com.baseProject.myBaseProject.dto.interview.VoiceTranscriptUpdateRequest;
import com.baseProject.myBaseProject.dto.interview.TextAnswerAcceptedResponse;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.exception.VoiceAttemptNotFoundException;
import com.baseProject.myBaseProject.interview.voice.AudioRecordingProcessor;
import com.baseProject.myBaseProject.interview.voice.AudioRecordingProcessor.ProcessedAudio;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore.PersistResult;
import com.baseProject.myBaseProject.interview.voice.VoiceAttemptStore.VoiceAttemptDraft;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionDispatcher;
import com.baseProject.myBaseProject.mapper.VoiceAttemptMapper;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;
import com.baseProject.myBaseProject.service.VoiceAttemptService;
import com.baseProject.myBaseProject.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceAttemptServiceImpl implements VoiceAttemptService {

    private static final String STORAGE_KEY_FORMAT =
            "interview-audio/%d/%d/answers/%s.%s";

    private final AudioRecordingProcessor audioProcessor;
    private final VoiceAttemptStore attemptStore;
    private final VoiceAnswerAttemptRepository attemptRepository;
    private final VoiceAttemptMapper attemptMapper;
    private final FileStorageService fileStorage;
    private final VoiceTranscriptionDispatcher transcriptionDispatcher;

    @Override
    public VoiceAttemptResponse upload(
            Long userId,
            Long sessionId,
            VoiceAttemptUploadRequest request,
            MultipartFile file) {
        ProcessedAudio audio = audioProcessor.process(file, request.durationMs());
        String clientAttemptId = request.clientAttemptId().strip();
        VoiceAttemptDraft preflight = draft(
                request,
                clientAttemptId,
                audio,
                "preflight-only");
        Optional<VoiceAnswerAttempt> replay = attemptStore.findReplayOrValidate(
                userId,
                sessionId,
                preflight);
        if (replay.isPresent()) {
            transcriptionDispatcher.dispatchAfterCommit(replay.get().getId());
            return attemptMapper.toResponse(replay.get());
        }

        String storageKey = STORAGE_KEY_FORMAT.formatted(
                userId,
                sessionId,
                UUID.randomUUID(),
                audio.format().extension());
        fileStorage.upload(storageKey, audio.content(), audio.format().contentType());

        PersistResult persisted;
        try {
            persisted = attemptStore.persist(
                    userId,
                    sessionId,
                    draft(request, clientAttemptId, audio, storageKey));
        } catch (RuntimeException persistenceFailure) {
            compensateUpload(userId, sessionId, storageKey);
            throw persistenceFailure;
        }
        if (!persisted.created()) {
            compensateUpload(userId, sessionId, storageKey);
        }
        transcriptionDispatcher.dispatchAfterCommit(persisted.attempt().getId());
        return attemptMapper.toResponse(persisted.attempt());
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceAttemptResponse get(Long userId, Long sessionId, Long attemptId) {
        return attemptRepository.findByIdAndSessionIdAndSessionUserId(
                        attemptId,
                        sessionId,
                        userId)
                .map(attemptMapper::toResponse)
                .orElseThrow(VoiceAttemptNotFoundException::new);
    }

    @Override
    public VoiceAttemptResponse editTranscript(
            Long userId,
            Long sessionId,
            Long attemptId,
            VoiceTranscriptUpdateRequest request) {
        return attemptMapper.toResponse(attemptStore.editTranscript(
                userId,
                sessionId,
                attemptId,
                request.editedText(),
                request.expectedAttemptVersion()));
    }

    @Override
    public TextAnswerAcceptedResponse confirm(
            Long userId,
            Long sessionId,
            Long attemptId,
            VoiceAttemptConfirmRequest request) {
        return attemptStore.confirm(
                userId,
                sessionId,
                attemptId,
                request.clientTurnId(),
                request.expectedSessionVersion(),
                request.expectedAttemptVersion());
    }

    private VoiceAttemptDraft draft(
            VoiceAttemptUploadRequest request,
            String clientAttemptId,
            ProcessedAudio audio,
            String storageKey) {
        return new VoiceAttemptDraft(
                request.promptTurnId(),
                clientAttemptId,
                request.expectedVersion(),
                storageKey,
                audio.format(),
                audio.content().length,
                audio.durationMs(),
                audio.checksumSha256());
    }

    private void compensateUpload(Long userId, Long sessionId, String storageKey) {
        try {
            fileStorage.delete(storageKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn(
                    "Voice upload compensation failed: userId={}, sessionId={}, exceptionType={}",
                    userId,
                    sessionId,
                    cleanupFailure.getClass().getSimpleName());
        }
    }
}
