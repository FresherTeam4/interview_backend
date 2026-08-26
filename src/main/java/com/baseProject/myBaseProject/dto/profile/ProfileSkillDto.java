package com.baseProject.myBaseProject.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Một kỹ năng, dùng cho cả response và body của {@code PUT} — xem
 * {@link ProfileEducationDto} về quy tắc {@code id} và hai trường server tự quản.
 *
 * <p>{@code name} bị chuẩn hóa (trim, gộp khoảng trắng) trước khi ghi, và trùng tên không
 * phân biệt hoa thường trong cùng một payload bị chặn bằng {@code DUPLICATE_SKILL_NAME}:
 * collation của cột đã coi "React" và "react" là một, nên không chặn ở tầng service thì
 * MySQL sẽ chặn bằng {@code uq_profile_skills_profile_name} với một thông báo khó đọc hơn.
 */
public record ProfileSkillDto(
        /** {@code null} = thêm mới. */
        Long id,

        @NotBlank(message = "Tên kỹ năng không được để trống")
        @Size(max = 80, message = "Tên kỹ năng tối đa 80 ký tự")
        String name,

        /** LANGUAGE | FRAMEWORK | DATABASE | TOOL | SOFT — không cưỡng chế theo enum. */
        @Size(max = 50, message = "Nhóm kỹ năng tối đa 50 ký tự")
        String category,

        /** Kiểu bọc, không phải nguyên thủy — lý do ở {@link ProfileEducationDto#userEdited()}. */
        Boolean userEdited,
        Short displayOrder
) {
}
