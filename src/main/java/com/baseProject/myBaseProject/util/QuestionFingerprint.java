package com.baseProject.myBaseProject.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class QuestionFingerprint {
    private QuestionFingerprint() {
    }

    public static String canonicalize(String content) {
        if (content == null) {
            return "";
        }
        return content.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    public static String sha256(String content) {
        return sha256Bytes(canonicalize(content).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Bytes(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
