package com.baseProject.myBaseProject.interview.generation;

import com.baseProject.myBaseProject.enums.QuestionSourceType;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class QuestionSignatureFactory {

    public String createSignature(
            QuestionSourceType sourceType,
            Long sourceProjectId,
            Long sourceSkillId,
            String competency,
            String concept) {
        String material = String.join("|",
                sourceType.name(),
                sourceProjectId == null ? "-" : sourceProjectId.toString(),
                sourceSkillId == null ? "-" : sourceSkillId.toString(),
                normalizeText(competency),
                normalizeText(concept));
        return sha256Hex(material);
    }

    public String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but not available", exception);
        }
    }
}
