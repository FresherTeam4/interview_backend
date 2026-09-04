package com.baseProject.myBaseProject.interview.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.SessionQuestion;
import com.baseProject.myBaseProject.entity.SessionTurn;
import com.baseProject.myBaseProject.entity.VoiceAnswerAttempt;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.enums.VoiceAttemptStatus;
import com.baseProject.myBaseProject.exception.SpeechTranscriptionException;
import com.baseProject.myBaseProject.interview.voice.SpeechToTextClient.TranscriptionOutcome;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionStore.FailureOutcome;
import com.baseProject.myBaseProject.interview.voice.VoiceTranscriptionStore.TranscriptionWork;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.VoiceAnswerAttemptRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class VoiceTranscriptionStoreTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final UUID TOKEN = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private VoiceAnswerAttemptRepository attemptRepository;
    @Mock
    private InterviewSession session;
    @Mock
    private SessionTurn prompt;
    @Mock
    private SessionQuestion question;

    private VoiceTranscriptionStore store;
    private VoiceAnswerAttempt current;
    private VoiceAnswerAttempt older;
    private TranscriptionWork work;

    @BeforeEach
    void setUp() {
        store = new VoiceTranscriptionStore(
                sessionRepository,
                attemptRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(prompt.getQuestion()).thenReturn(question);
        current = attempt((short) 2, "attempt-2");
        older = attempt((short) 1, "attempt-1");
        older.completeTranscription("Bản tốt cũ", "gemini-test", null, NOW.minusSeconds(5));
        claim(current, (short) 1);
        work = new TranscriptionWork(302L, 42L, "key.webm", "audio/webm", "vi-VN");
        when(sessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(attemptRepository.findWithSessionByIdForUpdate(302L))
                .thenReturn(Optional.of(current));
    }

    @Test
    void successfulRerecordKeepsRawImmutableAndDiscardsOlderGoodDraft() {
        when(prompt.getId()).thenReturn(205L);
        when(attemptRepository.findPromptAttemptsForUpdate(42L, 205L))
                .thenReturn(List.of(current, older));

        boolean committed = store.complete(
                work,
                TOKEN,
                new TranscriptionOutcome("Bản mới", "gemini-test", new BigDecimal("0.975")));

        assertThat(committed).isTrue();
        assertThat(current.getStatus()).isEqualTo(VoiceAttemptStatus.TRANSCRIBED);
        assertThat(current.getRawText()).isEqualTo("Bản mới");
        assertThat(current.getEditedText()).isNull();
        assertThat(current.getSttProvider()).isEqualTo("gemini-test");
        assertThat(current.getSttConfidence()).isEqualByComparingTo("0.975");
        assertThat(older.getStatus()).isEqualTo(VoiceAttemptStatus.DISCARDED);
    }

    @Test
    void retryableFailureRetriesOnceThenPreservesRecordingAsFailed() {
        SpeechTranscriptionException timeout =
                SpeechTranscriptionException.timeout(new RuntimeException("timeout"));

        FailureOutcome first = store.fail(work, TOKEN, timeout);

        assertThat(first.retryAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(current.getStatus()).isEqualTo(VoiceAttemptStatus.RECORDED);
        assertThat(current.getNextRetryAt()).isEqualTo(NOW.plusSeconds(1));

        claim(current, (short) 2);
        FailureOutcome second = store.fail(work, TOKEN, timeout);

        assertThat(second.failed()).isTrue();
        assertThat(current.getStatus()).isEqualTo(VoiceAttemptStatus.FAILED);
        assertThat(current.getRawText()).isNull();
        assertThat(current.getStorageKey()).isEqualTo("key-2.webm");
    }

    private VoiceAnswerAttempt attempt(short number, String clientId) {
        VoiceAnswerAttempt attempt = VoiceAnswerAttempt.recorded(
                session,
                prompt,
                number,
                clientId,
                "key-" + number + ".webm",
                AudioFormat.WEBM_OPUS,
                100,
                5_000,
                "a".repeat(64),
                NOW.minusSeconds(number));
        ReflectionTestUtils.setField(attempt, "id", 300L + number);
        return attempt;
    }

    private void claim(VoiceAnswerAttempt attempt, short processingAttempts) {
        ReflectionTestUtils.setField(attempt, "status", VoiceAttemptStatus.TRANSCRIBING);
        ReflectionTestUtils.setField(attempt, "processingToken", TOKEN.toString());
        ReflectionTestUtils.setField(attempt, "processingStartedAt", NOW.minusSeconds(1));
        ReflectionTestUtils.setField(attempt, "processingAttempts", processingAttempts);
        ReflectionTestUtils.setField(attempt, "nextRetryAt", null);
    }
}
