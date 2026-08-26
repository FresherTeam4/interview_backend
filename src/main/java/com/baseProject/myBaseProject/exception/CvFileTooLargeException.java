package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * File vượt {@code app.cv.max-file-size-bytes}.
 *
 * <p>Hạn mức của servlet ({@code spring.servlet.multipart.max-file-size}) đặt cao hơn một
 * chút, để lỗi này — có câu tiếng Việt và có con số cụ thể — nói trước
 * {@code MaxUploadSizeExceededException}.
 */
public class CvFileTooLargeException extends DomainException {

    public CvFileTooLargeException(long maxFileSizeBytes) {
        super(ErrorCode.CV_FILE_TOO_LARGE, HttpStatus.CONTENT_TOO_LARGE,
                Message.CV_FILE_TOO_LARGE_LIMIT.formatted(maxFileSizeBytes / 1024 / 1024));
    }
}
