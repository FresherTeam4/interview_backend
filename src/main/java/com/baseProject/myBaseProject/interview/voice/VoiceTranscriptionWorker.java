package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.exception.SpeechTranscriptionException;
import com.baseProject.myBaseProject.exception.StorageUnavailableException;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionInput;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionOutcome;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionStore.FailureOutcome;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionStore.TranscriptionWork;
import com.baseProject.myBaseProject.storage.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceTranscriptionWorker {

    private final VoiceTranscriptionStore store;
    private final FileStorageService fileStorage;
    private final SpeechToTextClient speechToTextClient;

    public Optional<Instant> process(Long attemptId, UUID processingToken) {
        long startedNanos = System.nanoTime();
        String workerOutcome = "ignored";
        TranscriptionWork work = store.prepare(attemptId, processingToken);
        if (work == null) {
            return Optional.empty();
        }

        try {
            byte[] audio = download(work.storageKey());
            TranscriptionOutcome outcome = speechToTextClient.transcribe(
                    new TranscriptionInput(audio, work.contentType(), work.languageCode()));
            workerOutcome = store.complete(work, processingToken, outcome)
                    ? "transcribed"
                    : "ignored";
            return Optional.empty();
        } catch (SpeechTranscriptionException failure) {
            FailureOutcome result = store.fail(work, processingToken, failure);
            workerOutcome = result.ignored()
                    ? "ignored"
                    : result.failed() ? "failed" : "retry_scheduled";
            log.warn(
                    "Voice transcription ended: attemptId={}, reason={}, retryable={}, outcome={}",
                    attemptId,
                    failure.getReason(),
                    failure.isRetryable(),
                    workerOutcome);
            return Optional.ofNullable(result.retryAt());
        } catch (RuntimeException unexpected) {
            FailureOutcome result = store.fail(
                    work,
                    processingToken,
                    SpeechTranscriptionException.unexpected(unexpected));
            workerOutcome = result.ignored() ? "ignored" : "failed";
            log.error(
                    "Unexpected voice transcription failure: attemptId={}, exceptionType={}, outcome={}",
                    attemptId,
                    unexpected.getClass().getSimpleName(),
                    workerOutcome);
            return Optional.ofNullable(result.retryAt());
        } finally {
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            log.info(
                    "Voice transcription worker finished: attemptId={}, outcome={}, durationMs={}",
                    attemptId,
                    workerOutcome,
                    durationMs);
        }
    }

    private byte[] download(String storageKey) {
        try {
            return fileStorage.download(storageKey);
        } catch (StorageUnavailableException exception) {
            throw SpeechTranscriptionException.audioUnavailable(exception);
        }
    }
}
