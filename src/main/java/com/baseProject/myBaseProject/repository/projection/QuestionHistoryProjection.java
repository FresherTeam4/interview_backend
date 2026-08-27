package com.baseProject.myBaseProject.repository.projection;

public record QuestionHistoryProjection(
        Long sessionId,
        String questionSignature,
        String questionText) {
}
