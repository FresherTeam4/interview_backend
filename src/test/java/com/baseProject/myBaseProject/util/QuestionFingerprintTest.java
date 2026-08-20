package com.baseProject.myBaseProject.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QuestionFingerprintTest {
    @Test
    void ignoresCaseAndRepeatedWhitespace() {
        assertEquals(
                QuestionFingerprint.sha256("  REST   API là gì? "),
                QuestionFingerprint.sha256("rest api LÀ GÌ?")
        );
    }

    @Test
    void keepsPunctuationSignificant() {
        assertNotEquals(
                QuestionFingerprint.sha256("REST API là gì?"),
                QuestionFingerprint.sha256("REST API là gì")
        );
    }
}
