package com.baseProject.myBaseProject.interview.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.exception.SpeechTranscriptionException;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionOutcome;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionStore.FailureOutcome;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionStore.TranscriptionWork;
import com.baseProject.myBaseProject.storage.FileStorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class VoiceTranscriptionWorkerTest {

    private static final Long ATTEMPT_ID = 301L;
    private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final byte[] AUDIO = {1, 2, 3};
    private static final TranscriptionWork WORK = new TranscriptionWork(
            ATTEMPT_ID,
            42L,
            "interview-audio/7/42/answers/test.webm",
            "audio/webm",
            "vi-VN");

    @Mock
    private VoiceTranscriptionStore store;
    @Mock
    private FileStorageService fileStorage;
    @Mock
    private SpeechToTextClient speechToTextClient;

    private VoiceTranscriptionWorker worker;

    @BeforeEach
    void setUp() {
        worker = new VoiceTranscriptionWorker(store, fileStorage, speechToTextClient);
        when(store.prepare(ATTEMPT_ID, TOKEN)).thenReturn(WORK);
        when(fileStorage.download(WORK.storageKey())).thenReturn(AUDIO);
    }

    @Test
    void successPersistsProviderTranscriptWithoutLoggingOrChangingIt() {
        TranscriptionOutcome outcome = new TranscriptionOutcome(
                "Câu trả lời nguyên bản",
                "gemini-test",
                null);
        when(speechToTextClient.transcribe(any())).thenReturn(outcome);
        when(store.complete(WORK, TOKEN, outcome)).thenReturn(true);

        assertThat(worker.process(ATTEMPT_ID, TOKEN)).isEmpty();

        verify(store).complete(WORK, TOKEN, outcome);
        verify(store, never()).fail(any(), any(), any());
    }

    @Test
    void timeoutSchedulesOneDurableRetry() {
        SpeechTranscriptionException timeout =
                SpeechTranscriptionException.timeout(new RuntimeException("timeout"));
        Instant retryAt = Instant.parse("2026-08-26T08:00:01Z");
        when(speechToTextClient.transcribe(any())).thenThrow(timeout);
        when(store.fail(WORK, TOKEN, timeout)).thenReturn(FailureOutcome.retryAt(retryAt));

        assertThat(worker.process(ATTEMPT_ID, TOKEN)).contains(retryAt);
    }

    @Test
    void malformedResponseBecomesTerminalAttemptFailure() {
        SpeechTranscriptionException malformed =
                SpeechTranscriptionException.malformedOutput(null);
        when(speechToTextClient.transcribe(any())).thenThrow(malformed);
        when(store.fail(WORK, TOKEN, malformed))
                .thenReturn(FailureOutcome.terminalFailure());

        assertThat(worker.process(ATTEMPT_ID, TOKEN)).isEmpty();
        verify(store).fail(WORK, TOKEN, malformed);
    }

    @Test
    void missingCredentialDoesNotRetryForever() {
        SpeechTranscriptionException missing = SpeechTranscriptionException.noApiKey();
        when(speechToTextClient.transcribe(any())).thenThrow(missing);
        when(store.fail(WORK, TOKEN, missing))
                .thenReturn(FailureOutcome.terminalFailure());

        assertThat(worker.process(ATTEMPT_ID, TOKEN)).isEmpty();
        verify(store).fail(WORK, TOKEN, missing);
    }
}
