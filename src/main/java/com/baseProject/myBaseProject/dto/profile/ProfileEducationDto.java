package com.baseProject.myBaseProject.dto.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Một mục học vấn, dùng cho cả hai chiều: nằm trong response của
 * {@code GET /api/profiles/{id}} và nằm trong body của {@code PUT /api/profiles/{id}}.
 *
 * <p>Lúc gửi lên: có {@code id} là sửa hàng đó, không có {@code id} là thêm mới. Hai trường
 * {@code userEdited} và {@code displayOrder} do server tự quản — gửi lên cũng bị bỏ qua
 * ({@code displayOrder} lấy theo vị trí trong mảng, {@code userEdited} bật khi hàng thay đổi thật).
 *
 * <p>Giới hạn ở đây bám sát DDL của {@code profile_educations} để người dùng nhận
 * {@code 400 VALIDATION_FAILED} kèm tên trường, thay vì {@code 409} từ constraint của MySQL.
 */
public record ProfileEducationDto(
        /** {@code null} = thêm mới. */
        Long id,

        @NotBlank(message = "Tên trường không được để trống")
        @Size(max = 255, message = "Tên trường tối đa 255 ký tự")
        String school,

        @Size(max = 150, message = "Bằng cấp tối đa 150 ký tự")
        String degree,

        @Size(max = 150, message = "Chuyên ngành tối đa 150 ký tự")
        String fieldOfStudy,

        @Min(value = 1900, message = "Năm bắt đầu phải từ 1900 đến 2100")
        @Max(value = 2100, message = "Năm bắt đầu phải từ 1900 đến 2100")
        Short startYear,

        @Min(value = 1900, message = "Năm kết thúc phải từ 1900 đến 2100")
        @Max(value = 2100, message = "Năm kết thúc phải từ 1900 đến 2100")
        Short endYear,

        /**
         * Kiểu bọc, không phải {@code boolean}: Jackson 3 bật
         * {@code FAIL_ON_NULL_FOR_PRIMITIVES} theo mặc định, nên một trường nguyên thủy <em>vắng
         * mặt</em> trong JSON gửi lên làm vỡ cả body với {@code 400 MALFORMED_REQUEST} — record thì
         * constructor chính là creator, thiếu tham số là Jackson truyền {@code null} vào
         * {@code boolean}. Hai trường này do server quản, nghĩa là client được phép không gửi; để
         * kiểu nguyên thủy thì "được phép không gửi" lặng lẽ thành "bắt buộc phải gửi".
         *
         * <p>Đã thử hai cách gọn hơn và cả hai đều không được:
         * {@code @JsonProperty(access = READ_ONLY)} còn tệ hơn (vỡ cả khi client có gửi, vì Jackson
         * bỏ qua giá trị đọc được rồi vẫn truyền {@code null} vào creator), còn
         * {@code @JsonSetter(nulls = SKIP)} chỉ chặn {@code null} tường minh, không chặn trường
         * vắng mặt.
         *
         * <p>Response không đổi một byte: {@code ProfileMapper.toDto} luôn truyền giá trị thật từ
         * entity, nên hai trường này chưa bao giờ ra ngoài dưới dạng {@code null}.
         */
        Boolean userEdited,
        Short displayOrder
) {
    /**
     * Ràng buộc chéo hai trường, tương ứng {@code chk_profile_educations_years}.
     * {@code @JsonIgnore} để nó không lẫn vào JSON trả về như một trường thật.
     */
    @JsonIgnore
    @AssertTrue(message = "Năm kết thúc phải lớn hơn hoặc bằng năm bắt đầu")
    public boolean isYearOrderValid() {
        return startYear == null || endYear == null || endYear >= startYear;
    }
}
