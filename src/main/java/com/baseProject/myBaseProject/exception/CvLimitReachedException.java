package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * Đã giữ đủ {@code app.cv.max-per-user} CV chưa xóa.
 *
 * <p>Trần này là thứ duy nhất chặn spam upload, vì bản nhiều CV không còn cơ chế ghi đè:
 * mỗi CV mới là một lần gọi Gemini phải trả tiền và một object nằm lại trên storage.
 */
public class CvLimitReachedException extends DomainException {

    public CvLimitReachedException(int maxPerUser) {
        super(ErrorCode.CV_LIMIT_REACHED, HttpStatus.CONFLICT,
                Message.CV_LIMIT_REACHED.formatted(maxPerUser));
    }
}
