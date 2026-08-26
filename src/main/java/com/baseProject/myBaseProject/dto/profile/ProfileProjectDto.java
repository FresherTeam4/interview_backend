package com.baseProject.myBaseProject.dto.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Một dự án, dùng cho cả response và body của {@code PUT} — xem {@link ProfileEducationDto}
 * về quy tắc {@code id} và hai trường server tự quản.
 *
 * <p>{@code techStack} là **nguyên chuỗi** như trong DB (nối bằng dấu phẩy), không tách mảng:
 * tách ra rồi lúc {@code PUT} lại phải nối vào, hai bên dễ lệch. Frontend {@code split(",")}
 * khi cần hiển thị chip.
 *
 * <p>{@code description} và {@code techStack} là cột {@code TEXT} nên DDL không giới hạn độ dài.
 * Hai giới hạn dưới đây là do tôi chọn: cột {@code TEXT} chỉ chứa 65535 **byte**, mà tiếng Việt
 * tốn 3 byte một chữ, nên không chặn ở đây thì một mô tả dài sẽ thành
 * {@code DataIntegrityViolationException} 409 khó hiểu thay vì 400 có tên trường.
 */
public record ProfileProjectDto(
        /** {@code null} = thêm mới. */
        Long id,

        @NotBlank(message = "Tên dự án không được để trống")
        @Size(max = 255, message = "Tên dự án tối đa 255 ký tự")
        String name,

        @Size(max = 5000, message = "Mô tả dự án tối đa 5000 ký tự")
        String description,

        @Size(max = 150, message = "Vai trò trong dự án tối đa 150 ký tự")
        String roleInProject,

        @Size(max = 500, message = "Danh sách công nghệ tối đa 500 ký tự")
        String techStack,

        LocalDate startDate,
        LocalDate endDate,

        /** Kiểu bọc, không phải nguyên thủy — lý do ở {@link ProfileEducationDto#userEdited()}. */
        Boolean userEdited,
        Short displayOrder
) {
    /**
     * Ràng buộc chéo hai trường, tương ứng {@code chk_profile_projects_dates}.
     * {@code @JsonIgnore} để nó không lẫn vào JSON trả về như một trường thật.
     */
    @JsonIgnore
    @AssertTrue(message = "Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu")
    public boolean isDateOrderValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
