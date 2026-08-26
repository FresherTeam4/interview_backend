package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Một phần tử trong payload {@code PUT} mang {@code id} không thuộc hồ sơ đang sửa.
 *
 * <p>Đây là chốt chặn IDOR ở tầng sâu nhất: hồ sơ đã kiểm chủ sở hữu rồi, nhưng từng hàng
 * con vẫn phải kiểm riêng, không thì gửi {@code {"id": 55}} của người khác là sửa được
 * kỹ năng của họ.
 *
 * <p>Ba factory thay cho constructor công khai để nhãn tiếng Việt nằm đúng một chỗ.
 */
public class ProfileItemNotFoundException extends DomainException {

    private ProfileItemNotFoundException(String itemKind, Long itemId) {
        super(ErrorCode.PROFILE_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND,
                Message.PROFILE_ITEM_NOT_FOUND.formatted(itemKind, itemId));
    }

    public static ProfileItemNotFoundException education(Long id) {
        return new ProfileItemNotFoundException("Học vấn", id);
    }

    public static ProfileItemNotFoundException skill(Long id) {
        return new ProfileItemNotFoundException("Kỹ năng", id);
    }

    public static ProfileItemNotFoundException project(Long id) {
        return new ProfileItemNotFoundException("Dự án", id);
    }
}
