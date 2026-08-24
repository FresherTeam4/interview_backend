package com.baseProject.myBaseProject.dto.cv;

/**
 * Kết quả một lần gọi AI đọc CV: dữ liệu đã bóc tách kèm thông tin để lưu vết
 * vào bảng {@code cv_parse_results}.
 */
public record CvParseOutcome(
        CvParsePayload payload,
        String rawJson,
        String modelName,
        int durationMs,
        Integer tokenCost
) { }
