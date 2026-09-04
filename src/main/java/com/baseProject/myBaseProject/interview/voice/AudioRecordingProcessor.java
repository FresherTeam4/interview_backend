package com.baseProject.myBaseProject.interview.voice;

import com.baseProject.myBaseProject.config.properites.VoiceProperties;
import com.baseProject.myBaseProject.enums.AudioFormat;
import com.baseProject.myBaseProject.exception.AudioDurationTooLongException;
import com.baseProject.myBaseProject.exception.AudioFileRequiredException;
import com.baseProject.myBaseProject.exception.AudioFileTooLargeException;
import com.baseProject.myBaseProject.exception.AudioInvalidException;
import com.baseProject.myBaseProject.exception.AudioInvalidFileTypeException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;

@Component
@RequiredArgsConstructor
public class AudioRecordingProcessor {

    private static final byte[] WEBM_MAGIC = {
            0x1A, 0x45, (byte) 0xDF, (byte) 0xA3
    };
    private static final byte[] WEBM_DOCTYPE = {'w', 'e', 'b', 'm'};
    private static final byte[] OPUS_CODEC = {'A', '_', 'O', 'P', 'U', 'S'};
    private static final byte[] MP4_FILE_TYPE = {'f', 't', 'y', 'p'};
    private static final byte[] AAC_CODEC = {'m', 'p', '4', 'a'};
    private static final byte[] MP4_MOVIE_HEADER = {'m', 'v', 'h', 'd'};
    private static final byte[] WEBM_TIMECODE_SCALE = {0x2A, (byte) 0xD7, (byte) 0xB1};
    private static final byte[] WEBM_DURATION = {0x44, (byte) 0x89};
    private static final long DEFAULT_WEBM_TIMECODE_SCALE = 1_000_000L;
    private static final long DURATION_TOLERANCE_FLOOR_MS = 2_000L;

    private final VoiceProperties properties;

    public ProcessedAudio process(MultipartFile file, Integer declaredDurationMs) {
        validateDeclaredDuration(declaredDurationMs);
        if (file == null || file.isEmpty()) {
            throw new AudioFileRequiredException();
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new AudioFileTooLargeException(properties.maxFileSizeBytes());
        }

        byte[] content = readFully(file);
        if (content.length == 0) {
            throw new AudioFileRequiredException();
        }
        if (content.length > properties.maxFileSizeBytes()) {
            throw new AudioFileTooLargeException(properties.maxFileSizeBytes());
        }

        AudioFormat format = detectFormat(content);
        int durationMs = verifiedDuration(content, format, declaredDurationMs);
        return new ProcessedAudio(
                format,
                content,
                durationMs,
                sha256Hex(content));
    }

    private void validateDeclaredDuration(Integer durationMs) {
        if (durationMs == null || durationMs <= 0) {
            throw new AudioInvalidException();
        }
        if (durationMs > properties.maxDurationMs()) {
            throw new AudioDurationTooLongException(properties.maxDurationMs());
        }
    }

    private byte[] readFully(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new AudioInvalidException(exception);
        }
    }

    private AudioFormat detectFormat(byte[] content) {
        if (startsWith(content, WEBM_MAGIC)
                && contains(content, WEBM_DOCTYPE)
                && contains(content, OPUS_CODEC)) {
            return AudioFormat.WEBM_OPUS;
        }
        if (content.length >= 12
                && matchesAt(content, 4, MP4_FILE_TYPE)
                && contains(content, AAC_CODEC)) {
            return AudioFormat.MP4_AAC;
        }
        throw new AudioInvalidFileTypeException();
    }

    private int verifiedDuration(
            byte[] content,
            AudioFormat format,
            int declaredDurationMs) {
        OptionalLong inspected = switch (format) {
            case WEBM_OPUS -> inspectWebmDuration(content);
            case MP4_AAC -> inspectMp4Duration(content);
        };
        if (inspected.isEmpty()) {
            return declaredDurationMs;
        }

        long inspectedDurationMs = inspected.getAsLong();
        if (inspectedDurationMs <= 0 || inspectedDurationMs > Integer.MAX_VALUE) {
            throw new AudioInvalidException();
        }
        if (inspectedDurationMs > properties.maxDurationMs()) {
            throw new AudioDurationTooLongException(properties.maxDurationMs());
        }
        long tolerance = Math.max(
                DURATION_TOLERANCE_FLOOR_MS,
                Math.round(inspectedDurationMs * 0.1d));
        if (Math.abs(inspectedDurationMs - declaredDurationMs) > tolerance) {
            throw new AudioInvalidException();
        }
        return Math.toIntExact(inspectedDurationMs);
    }

    private OptionalLong inspectMp4Duration(byte[] content) {
        int marker = indexOf(content, MP4_MOVIE_HEADER);
        if (marker < 0 || marker + 24 > content.length) {
            return OptionalLong.empty();
        }
        int version = Byte.toUnsignedInt(content[marker + 4]);
        int timescaleOffset;
        int durationOffset;
        boolean longDuration;
        if (version == 0) {
            timescaleOffset = marker + 16;
            durationOffset = marker + 20;
            longDuration = false;
        } else if (version == 1) {
            timescaleOffset = marker + 24;
            durationOffset = marker + 28;
            longDuration = true;
        } else {
            return OptionalLong.empty();
        }
        int required = durationOffset + (longDuration ? Long.BYTES : Integer.BYTES);
        if (required > content.length) {
            return OptionalLong.empty();
        }

        long timescale = readUnsignedInt(content, timescaleOffset);
        long duration = longDuration
                ? readPositiveLong(content, durationOffset)
                : readUnsignedInt(content, durationOffset);
        if (timescale <= 0 || duration <= 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.round(duration * 1000.0d / timescale));
    }

    private OptionalLong inspectWebmDuration(byte[] content) {
        Long durationBits = readEbmlElementValue(content, WEBM_DURATION, true);
        if (durationBits == null) {
            return OptionalLong.empty();
        }
        double duration = Double.longBitsToDouble(durationBits);
        if (!Double.isFinite(duration) || duration <= 0) {
            float floatDuration = Float.intBitsToFloat((int) (long) durationBits);
            duration = floatDuration;
        }
        if (!Double.isFinite(duration) || duration <= 0) {
            return OptionalLong.empty();
        }

        Long scale = readEbmlElementValue(content, WEBM_TIMECODE_SCALE, false);
        long timecodeScale = scale == null ? DEFAULT_WEBM_TIMECODE_SCALE : scale;
        return OptionalLong.of(Math.round(duration * timecodeScale / 1_000_000.0d));
    }

    private Long readEbmlElementValue(byte[] content, byte[] elementId, boolean floatingPoint) {
        int marker = indexOf(content, elementId);
        if (marker < 0) {
            return null;
        }
        EbmlSize size = readEbmlSize(content, marker + elementId.length);
        if (size == null || size.value() <= 0 || size.value() > Long.BYTES) {
            return null;
        }
        int valueOffset = marker + elementId.length + size.encodedLength();
        if (valueOffset + size.value() > content.length) {
            return null;
        }
        if (floatingPoint && size.value() == Float.BYTES) {
            int bits = ByteBuffer.wrap(content, valueOffset, Float.BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .getInt();
            return Double.doubleToLongBits(Float.intBitsToFloat(bits));
        }
        if (floatingPoint && size.value() != Double.BYTES) {
            return null;
        }

        long value = 0;
        for (int index = 0; index < size.value(); index++) {
            value = (value << 8) | Byte.toUnsignedLong(content[valueOffset + index]);
        }
        return value;
    }

    private EbmlSize readEbmlSize(byte[] content, int offset) {
        if (offset >= content.length) {
            return null;
        }
        int first = Byte.toUnsignedInt(content[offset]);
        int marker = 0x80;
        int length = 1;
        while (length <= 8 && (first & marker) == 0) {
            marker >>= 1;
            length++;
        }
        if (length > 8 || offset + length > content.length) {
            return null;
        }
        long value = first & (marker - 1);
        for (int index = 1; index < length; index++) {
            value = (value << 8) | Byte.toUnsignedLong(content[offset + index]);
        }
        if (value > Integer.MAX_VALUE) {
            return null;
        }
        return new EbmlSize((int) value, length);
    }

    private long readUnsignedInt(byte[] content, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(content, offset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt());
    }

    private long readPositiveLong(byte[] content, int offset) {
        long value = ByteBuffer.wrap(content, offset, Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
        return value < 0 ? 0 : value;
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but not available", exception);
        }
    }

    private boolean startsWith(byte[] content, byte[] expected) {
        return matchesAt(content, 0, expected);
    }

    private boolean contains(byte[] content, byte[] expected) {
        return indexOf(content, expected) >= 0;
    }

    private int indexOf(byte[] content, byte[] expected) {
        for (int offset = 0; offset <= content.length - expected.length; offset++) {
            if (matchesAt(content, offset, expected)) {
                return offset;
            }
        }
        return -1;
    }

    private boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (offset < 0 || offset + expected.length > content.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    public record ProcessedAudio(
            AudioFormat format,
            byte[] content,
            int durationMs,
            String checksumSha256) {
    }

    private record EbmlSize(int value, int encodedLength) {
    }
}
