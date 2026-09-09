package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.SpeechProperties;
import com.baseProject.myBaseProject.entity.InterviewSession;
import com.baseProject.myBaseProject.entity.InterviewTurn;
import com.baseProject.myBaseProject.enums.InterviewSessionStatus;
import com.baseProject.myBaseProject.enums.InterviewTurnRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.InterviewSessionRepository;
import com.baseProject.myBaseProject.repository.InterviewTurnRepository;
import com.baseProject.myBaseProject.speech.SpeechProviderRegistry;
import com.baseProject.myBaseProject.speech.SpeechToTextProvider;
import com.baseProject.myBaseProject.speech.TextToSpeechProvider;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionRequest;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import com.baseProject.myBaseProject.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechServiceImplTest {
    @Mock
    private InterviewSessionRepository sessions;
    @Mock
    private InterviewTurnRepository turns;
    @Mock
    private SpeechProviderRegistry providers;
    @Mock
    private SpeechToTextProvider speechToTextProvider;
    @Mock
    private TextToSpeechProvider textToSpeechProvider;
    @Mock
    private StorageService storage;

    private SpeechServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpeechServiceImpl(
                sessions, turns, providers, properties(true), storage);
    }

    @Test
    void transcribeUsesConfiguredProviderAndSessionLanguage() {
        InterviewSession session = InterviewSession.builder()
                .id(51L)
                .languageCode("ja")
                .status(InterviewSessionStatus.IN_PROGRESS)
                .build();
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "answer.webm", "audio/webm", new byte[]{1, 2, 3});
        when(sessions.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(session));
        when(providers.speechToText("elevenlabs")).thenReturn(speechToTextProvider);
        when(speechToTextProvider.transcribe(any()))
                .thenReturn(new SpeechTranscriptionResult("  回答です。 ", "ja"));

        SpeechTranscriptionResult result = service.transcribe(7L, 51L, audio);

        assertThat(result.text()).isEqualTo("回答です。");
        assertThat(result.languageCode()).isEqualTo("ja");
        ArgumentCaptor<SpeechTranscriptionRequest> request =
                ArgumentCaptor.forClass(SpeechTranscriptionRequest.class);
        verify(speechToTextProvider).transcribe(request.capture());
        assertThat(request.getValue().languageCode()).isEqualTo("ja");
        assertThat(request.getValue().audio()).containsExactly(1, 2, 3);
    }

    @Test
    void synthesizeReturnsCachedInterviewerAudioWithoutCallingProvider() {
        InterviewSession session = InterviewSession.builder()
                .id(51L)
                .languageCode("vi")
                .build();
        InterviewTurn turn = InterviewTurn.builder()
                .id(91L)
                .session(session)
                .role(InterviewTurnRole.INTERVIEWER)
                .contentText("Bạn hãy giới thiệu về bản thân.")
                .build();
        byte[] cachedAudio = {4, 5, 6};
        when(sessions.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(session));
        when(turns.findByIdAndSessionId(91L, 51L)).thenReturn(Optional.of(turn));
        when(providers.textToSpeech("elevenlabs")).thenReturn(textToSpeechProvider);
        when(textToSpeechProvider.cacheIdentity("vi"))
                .thenReturn("elevenlabs:flash:voice-vi");
        when(textToSpeechProvider.outputContentType()).thenReturn("audio/mpeg");
        when(storage.exists(anyString())).thenReturn(true);
        when(storage.download(anyString())).thenReturn(cachedAudio);

        SpeechSynthesisResult result = service.synthesizeInterviewerTurn(7L, 51L, 91L);

        assertThat(result.audio()).containsExactly(cachedAudio);
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        verify(textToSpeechProvider, never()).synthesize(any());
    }

    @Test
    void synthesizeRejectsCandidateTurn() {
        InterviewSession session = InterviewSession.builder()
                .id(51L)
                .languageCode("en")
                .build();
        InterviewTurn turn = InterviewTurn.builder()
                .id(92L)
                .session(session)
                .role(InterviewTurnRole.CANDIDATE)
                .contentText("My answer")
                .build();
        when(sessions.findByIdAndUserId(51L, 7L)).thenReturn(Optional.of(session));
        when(turns.findByIdAndSessionId(92L, 51L)).thenReturn(Optional.of(turn));

        assertThatThrownBy(() -> service.synthesizeInterviewerTurn(7L, 51L, 92L))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.SPEECH_TURN_NOT_SYNTHESIZABLE));

        verify(providers, never()).textToSpeech(anyString());
    }

    @Test
    void disabledSpeechStopsBeforeReadingSession() {
        service = new SpeechServiceImpl(
                sessions, turns, providers, properties(false), storage);
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "answer.webm", "audio/webm", new byte[]{1});

        assertThatThrownBy(() -> service.transcribe(7L, 51L, audio))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.SPEECH_NOT_ENABLED));

        verify(sessions, never()).findByIdAndUserId(any(), any());
    }

    private SpeechProperties properties(boolean enabled) {
        SpeechProperties.ElevenLabs elevenLabs = new SpeechProperties.ElevenLabs(
                "key",
                URI.create("https://api.elevenlabs.io"),
                "scribe_v2",
                "eleven_flash_v2_5",
                "mp3_44100_128",
                Map.of("en", "voice-en", "ja", "voice-ja", "vi", "voice-vi"));
        return new SpeechProperties(
                enabled,
                "elevenlabs",
                "elevenlabs",
                1024,
                Duration.ofSeconds(5),
                Duration.ofSeconds(45),
                new SpeechProperties.Providers(elevenLabs));
    }
}
