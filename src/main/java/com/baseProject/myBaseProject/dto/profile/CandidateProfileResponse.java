package com.baseProject.myBaseProject.dto.profile;

import com.baseProject.myBaseProject.enums.ProfileSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Hồ sơ đầy đủ, trả về ở {@code GET /api/profiles/{id}}, {@code PUT /api/profiles/{id}} và
 * {@code POST /api/profiles/{id}/confirm}.
 *
 * <p>Cả 3 danh sách con nằm trong một lần gọi vì form sửa cần toàn bộ; chia thành 4 request
 * chỉ tạo trạng thái nửa vời trên UI khi một trong số đó lỗi.
 *
 * <p>{@code raw_json} của lần bóc tách **không xuất hiện ở đây**, cũng không ở endpoint nào
 * khác của người dùng. Đó chính là cách "bản đã sửa luôn được ưu tiên hơn bản tự động" thành
 * đúng mà không cần cột cờ nào: không tồn tại đường ghi {@code raw_json} đè lên hồ sơ.
 */
public record CandidateProfileResponse(
        Long id,
        Long cvDocumentId,
        String cvOriginalFilename,
        String headline,
        BigDecimal yearsExperience,
        String targetPosition,
        String seniorityLevel,

        /** Chỉ để thống kê tỉ lệ AI bóc tách sai, không phải cờ ưu tiên. */
        ProfileSource source,

        /** {@code null} = chưa xác nhận, và chưa chọn được lúc tạo phiên phỏng vấn. */
        Instant confirmedAt,

        Instant createdAt,
        Instant updatedAt,

        List<ProfileEducationDto> educations,
        List<ProfileSkillDto> skills,
        List<ProfileProjectDto> projects
) {
}
