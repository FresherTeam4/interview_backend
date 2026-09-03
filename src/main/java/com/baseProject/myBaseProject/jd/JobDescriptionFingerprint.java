package com.baseProject.myBaseProject.jd;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class JobDescriptionFingerprint {

    /** Tạo SHA-256 ổn định sau khi chuẩn hóa ký tự xuống dòng của JD. */
    public String create(String jobDescriptionText) {
        if (jobDescriptionText == null) {
            throw new IllegalArgumentException("Job description must not be null");
        }
        return sha256Hex(normalizeLineEndings(jobDescriptionText));
    }

    /** Chuẩn hóa CRLF và CR về LF để cùng nội dung có cùng fingerprint. */
    private String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Băm nội dung thành chuỗi SHA-256 chữ thường. */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but not available", exception);
        }
    }
}
