package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Bóc tách lại hoặc xóa một CV đang {@code PARSING}.
 *
 * <p>Chặn xóa vì job nền đang chạy sẽ ghi hồ sơ cho một CV vừa bị ẩn khỏi danh sách; chặn
 * bóc tách lại vì lần đang chạy chưa chắc thất bại, gọi thêm là trả tiền API hai lần.
 */
public class CvParseInProgressException extends DomainException {

    public CvParseInProgressException() {
        super(ErrorCode.CV_PARSE_IN_PROGRESS, HttpStatus.CONFLICT, Message.CV_PARSE_IN_PROGRESS);
    }
}
