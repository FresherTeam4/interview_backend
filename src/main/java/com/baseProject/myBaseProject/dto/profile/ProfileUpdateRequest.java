package com.baseProject.myBaseProject.dto.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProfileUpdateRequest(
        @Size(max = 255, message = "Headline tối đa 255 ký tự")
        String headline,

        @DecimalMin(value = "0.0", message = "Số năm kinh nghiệm không được âm")
        @Digits(integer = 2, fraction = 1,
                message = "Số năm kinh nghiệm tối đa 99.9 và chỉ một chữ số thập phân")
        BigDecimal yearsExperience,

        @Size(max = 150, message = "Vị trí mong muốn tối đa 150 ký tự")
        String targetPosition,

        @Size(max = 30, message = "Cấp độ tối đa 30 ký tự")
        String seniorityLevel,

        @NotNull(message = "Thiếu danh sách học vấn")
        @Size(max = 20, message = "Tối đa 20 mục học vấn")
        @Valid
        List<ProfileEducationDto> educations,

        @NotNull(message = "Thiếu danh sách kỹ năng")
        @Size(max = 100, message = "Tối đa 100 kỹ năng")
        @Valid
        List<ProfileSkillDto> skills,

        @NotNull(message = "Thiếu danh sách dự án")
        @Size(max = 50, message = "Tối đa 50 dự án")
        @Valid
        List<ProfileProjectDto> projects
) {
}
