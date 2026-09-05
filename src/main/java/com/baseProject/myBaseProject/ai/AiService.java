package com.baseProject.myBaseProject.ai;

import java.util.Map;

public interface AiService {

    String generateText(String prompt);

    // AI trả về dữ liệu có cấu trúc ánh xạ trực tiếp vào DTO
    <T> T generateStructured(String prompt, Map<String, Object> params, Class<T> responseClass);

    default <T> T generateStructured(String prompt, Class<T> responseClass) {
        return generateStructured(prompt, Map.of(), responseClass);
    }

    <T> T generateStructured(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> params,
            Class<T> responseClass);

    // AI phân tích trực tiếp file PDF và trả về dữ liệu có cấu trúc
    <T> T generateStructuredWithPdf(String prompt, Map<String, Object> params, byte[] pdfBytes, Class<T> responseClass);

    default <T> T generateStructuredWithPdf(String prompt, byte[] pdfBytes, Class<T> responseClass) {
        return generateStructuredWithPdf(prompt, Map.of(), pdfBytes, responseClass);
    }
}
