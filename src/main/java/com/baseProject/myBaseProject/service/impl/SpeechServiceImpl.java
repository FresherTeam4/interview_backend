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
import com.baseProject.myBaseProject.service.SpeechService;
import com.baseProject.myBaseProject.speech.SpeechProviderRegistry;
import com.baseProject.myBaseProject.speech.SpeechToTextProvider;
import com.baseProject.myBaseProject.speech.TextToSpeechProvider;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisRequest;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionRequest;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import com.baseProject.myBaseProject.storage.StorageService;
import com.baseProject.myBaseProject.util.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class SpeechServiceImpl implements SpeechService {
    private final InterviewSessionRepository sessions;
    private final InterviewTurnRepository turns;
    private final SpeechProviderRegistry providers;
    private final SpeechProperties properties;
    private final StorageService storage;

    public SpeechServiceImpl(
            InterviewSessionRepository sessions,
            InterviewTurnRepository turns,
            SpeechProviderRegistry providers,
            SpeechProperties properties,
            StorageService storage) {
        this.sessions = sessions;
        this.turns = turns;
        this.providers = providers;
        this.properties = properties;
        this.storage = storage;
    }

    @Override
    public SpeechTranscriptionResult transcribe(
            Long userId, Long sessionId, MultipartFile audio) {
        requireEnabled();
        InterviewSession session = ownedSession(userId, sessionId);
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new DomainException(ErrorCode.INTERVIEW_SESSION_NOT_IN_PROGRESS);
        }
        validateAudio(audio);

        SpeechToTextProvider provider = providers.speechToText(properties.sttProvider());
        // Dùng ngôn ngữ đã lưu trong session để client không thể gửi lệch ngôn ngữ phỏng vấn.
        SpeechTranscriptionResult result = provider.transcribe(new SpeechTranscriptionRequest(
                readAudio(audio),
                audio.getOriginalFilename(),
                audio.getContentType(),
                session.getLanguageCode()));
        if (result == null || result.text() == null || result.text().isBlank()) {
            throw new DomainException(ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        }

        return new SpeechTranscriptionResult(
                result.text().strip(), session.getLanguageCode());
    }

    @Override
    public SpeechSynthesisResult synthesizeInterviewerTurn(
            Long userId, Long sessionId, Long turnId) {
        requireEnabled();
        InterviewSession session = ownedSession(userId, sessionId);
        InterviewTurn turn = turns.findByIdAndSessionId(turnId, sessionId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.SPEECH_TURN_NOT_SYNTHESIZABLE));
        if (turn.getRole() != InterviewTurnRole.INTERVIEWER) {
            throw new DomainException(ErrorCode.SPEECH_TURN_NOT_SYNTHESIZABLE);
        }

        TextToSpeechProvider provider = providers.textToSpeech(properties.ttsProvider());
        String cacheKey = cacheKey(sessionId, turn, provider, session.getLanguageCode());
        // Dùng lại audio đã sinh để giảm độ trễ và tránh tính phí TTS cho cùng một lượt hỏi.
        if (storage.exists(cacheKey)) {
            return new SpeechSynthesisResult(
                    storage.download(cacheKey), provider.outputContentType());
        }

        SpeechSynthesisResult result = provider.synthesize(new SpeechSynthesisRequest(
                turn.getContentText(), session.getLanguageCode()));
        if (result == null || result.audio() == null || result.audio().length == 0) {
            throw new DomainException(ErrorCode.SPEECH_SYNTHESIS_FAILED);
        }
        storage.upload(cacheKey, result.audio(), result.contentType());

        return result;
    }

    private InterviewSession ownedSession(Long userId, Long sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DomainException(
                        ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new DomainException(ErrorCode.SPEECH_NOT_ENABLED);
        }
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new DomainException(ErrorCode.SPEECH_INVALID_AUDIO);
        }
        if (audio.getSize() > properties.maxAudioSizeBytes()) {
            throw new DomainException(ErrorCode.SPEECH_AUDIO_TOO_LARGE);
        }
        String contentType = audio.getContentType();
        // MediaRecorder có thể đóng audio trong container WebM hoặc MP4 tùy trình duyệt.
        if (contentType == null
                || (!contentType.startsWith("audio/")
                && !contentType.equals("video/webm")
                && !contentType.equals("video/mp4"))) {
            throw new DomainException(ErrorCode.SPEECH_INVALID_AUDIO);
        }
    }

    private byte[] readAudio(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException exception) {
            throw new DomainException(
                    ErrorCode.SPEECH_INVALID_AUDIO,
                    "Failed to read uploaded audio",
                    exception);
        }
    }

    private String cacheKey(
            Long sessionId,
            InterviewTurn turn,
            TextToSpeechProvider provider,
            String languageCode) {
        // Cache phải vô hiệu khi provider đổi model, voice hoặc định dạng đầu ra.
        String source = provider.cacheIdentity(languageCode)
                + '\n' + turn.getContentText();
        String fingerprint = Sha256.hex(source.getBytes(StandardCharsets.UTF_8));

        return "interviews/%d/speech/%d/%s.mp3"
                .formatted(sessionId, turn.getId(), fingerprint);
    }
}
