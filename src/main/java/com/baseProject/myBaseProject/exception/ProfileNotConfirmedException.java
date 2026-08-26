package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Dùng một hồ sơ chưa xác nhận vào việc đòi hỏi hồ sơ đã xác nhận.
 *
 * <p>Chưa có endpoint nào trong nhóm CV ném mã này: nó dành cho luồng tạo phiên phỏng vấn ở
 * TableGroup sau, nơi hồ sơ là dữ liệu đầu vào và {@code confirmed_at} là dấu hiệu người dùng
 * đã đọc lại phần AI bóc tách. Khai báo sẵn ở đây vì mã lỗi thuộc nhóm hồ sơ.
 */
public class ProfileNotConfirmedException extends DomainException {

    public ProfileNotConfirmedException() {
        super(ErrorCode.PROFILE_NOT_CONFIRMED, HttpStatus.CONFLICT, Message.PROFILE_NOT_CONFIRMED);
    }
}
