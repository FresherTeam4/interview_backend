package com.baseProject.myBaseProject.interview.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.exception.AudioDurationTooLongException;
import com.baseProject.myBaseProject.exception.AudioFileRequiredException;
import com.baseProject.myBaseProject.exception.AudioFileTooLargeException;
import com.baseProject.myBaseProject.exception.AudioInvalidException;
import com.baseProject.myBaseProject.exception.AudioInvalidFileTypeException;
import com.baseProject.myBaseProject.interview.voice.AudioRecordingProcessor.ProcessedAudio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

class AudioRecordingProcessorTest {

    private static final long MAX_FILE_SIZE = 1_000;
    private static final int MAX_DURATION_MS = 10_000;

    private AudioRecordingProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AudioRecordingProcessor(
                new VoiceProperties(
                        MAX_FILE_SIZE,
                        MAX_DURATION_MS,
                        30,
                        true,
                        "gemini-test",
                        "v1",
                        7_000,
                        30));
    }

    @Test
    void processDetectsWebmOpusFromContentAndVerifiesDuration() {
        byte[] content = webmOpus(5_000.0f);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "recording.bin",
                "application/octet-stream",
                content);

        ProcessedAudio result = processor.process(file, 5_100);

        assertThat(result.format()).isEqualTo(AudioFormat.WEBM_OPUS);
        assertThat(result.durationMs()).isEqualTo(5_000);
        assertThat(result.content()).isEqualTo(content);
        assertThat(result.checksumSha256()).hasSize(64);
    }

    @Test
    void processDetectsMp4AacFromContent() {
        ProcessedAudio result = processor.process(
                new MockMultipartFile("file", "voice.webm", "audio/webm", mp4Aac(5_000)),
                5_000);

        assertThat(result.format()).isEqualTo(AudioFormat.MP4_AAC);
        assertThat(result.durationMs()).isEqualTo(5_000);
    }

    @Test
    void processRejectsMissingOversizedAndUnsupportedFiles() {
        assertThatThrownBy(() -> processor.process(null, 1_000))
                .isInstanceOf(AudioFileRequiredException.class);
        assertThatThrownBy(() -> processor.process(
                        new MockMultipartFile("file", "voice.webm", "audio/webm", new byte[1_001]),
                        1_000))
                .isInstanceOf(AudioFileTooLargeException.class);
        assertThatThrownBy(() -> processor.process(
                        new MockMultipartFile(
                                "file",
                                "voice.webm",
                                "audio/webm",
                                "not audio".getBytes(StandardCharsets.UTF_8)),
                        1_000))
                .isInstanceOf(AudioInvalidFileTypeException.class);
    }

    @Test
    void processRejectsUntrustedOrExcessiveDurationMetadata() {
        MockMultipartFile fiveSecondAudio = new MockMultipartFile(
                "file", "voice.webm", "audio/webm", webmOpus(5_000.0f));

        assertThatThrownBy(() -> processor.process(fiveSecondAudio, 0))
                .isInstanceOf(AudioInvalidException.class);
        assertThatThrownBy(() -> processor.process(fiveSecondAudio, MAX_DURATION_MS + 1))
                .isInstanceOf(AudioDurationTooLongException.class);
        assertThatThrownBy(() -> processor.process(fiveSecondAudio, 9_000))
                .isInstanceOf(AudioInvalidException.class);
        assertThatThrownBy(() -> processor.process(
                        new MockMultipartFile(
                                "file", "voice.webm", "audio/webm", webmOpus(11_000.0f)),
                        9_500))
                .isInstanceOf(AudioDurationTooLongException.class);
    }

    private byte[] webmOpus(float durationMs) {
        ByteBuffer buffer = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN);
        buffer.put(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
        buffer.put("webm".getBytes(StandardCharsets.US_ASCII));
        buffer.put("A_OPUS".getBytes(StandardCharsets.US_ASCII));
        buffer.put(new byte[]{0x2A, (byte) 0xD7, (byte) 0xB1, (byte) 0x83});
        buffer.put(new byte[]{0x0F, 0x42, 0x40});
        buffer.put(new byte[]{0x44, (byte) 0x89, (byte) 0x84});
        buffer.putFloat(durationMs);
        return buffer.array();
    }

    private byte[] mp4Aac(int durationMs) {
        ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(16);
        buffer.put("ftyp".getBytes(StandardCharsets.US_ASCII));
        buffer.put("mp42".getBytes(StandardCharsets.US_ASCII));
        buffer.put("mp4a".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(32);
        buffer.put("mvhd".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(1_000);
        buffer.putInt(durationMs);
        return buffer.array();
    }
}
