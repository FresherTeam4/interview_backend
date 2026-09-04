package com.baseProject.myBaseProject.ai;

import java.util.List;
import java.util.Map;

public interface AiService {

    String generateText(String prompt);

    // AI trả về dữ liệu có cấu trúc ánh xạ trực tiếp vào DTO
    <T> T generateStructured(String prompt, Map<String, Object> params, Class<T> responseClass);

    default <T> T generateStructured(String prompt, Class<T> responseClass) {
        return generateStructured(prompt, Map.of(), responseClass);
    }

    // Vision AI phân tích ảnh và trả về dữ liệu có cấu trúc
    <T> T generateStructuredWithImages(String prompt, Map<String, Object> params, List<byte[]> imageBytesList, Class<T> responseClass);

    default <T> T generateStructuredWithImages(String prompt, List<byte[]> imageBytesList, Class<T> responseClass) {
        return generateStructuredWithImages(prompt, Map.of(), imageBytesList, responseClass);
    }
}
