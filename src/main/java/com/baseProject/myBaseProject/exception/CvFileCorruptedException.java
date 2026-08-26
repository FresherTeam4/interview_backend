package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * PDFBox không mở được file, hoặc file có mật khẩu.
 *
 * <p>Bắt tại máy mình để không tốn một lượt gọi API chỉ để biết file hỏng. {@code cause}
 * giữ lại lỗi gốc của PDFBox cho log, người dùng chỉ thấy câu tiếng Việt.
 */
public class CvFileCorruptedException extends DomainException {

    public CvFileCorruptedException() {
        super(ErrorCode.CV_FILE_CORRUPTED, HttpStatus.BAD_REQUEST, Message.CV_FILE_CORRUPTED);
    }

    public CvFileCorruptedException(Throwable cause) {
        super(ErrorCode.CV_FILE_CORRUPTED, HttpStatus.BAD_REQUEST, Message.CV_FILE_CORRUPTED,
                cause);
    }
}
