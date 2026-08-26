package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

import org.springframework.http.HttpStatus;

/**
 * MinIO/S3 không ghi, không đọc, hoặc không tạo được link tải.
 *
 * <p>503 chứ không 500: lỗi nằm ở một dịch vụ ngoài, và người dùng thử lại sau vài phút là
 * có thể xong, nên câu thông báo mời thử lại.
 *
 * <p>Luôn bọc nguyên nhân gốc: {@code SdkException} của AWS SDK mang thông tin thật (sai khóa,
 * bucket chưa có, không nối được endpoint) mà người dùng không được thấy nhưng log phải giữ.
 */
public class StorageUnavailableException extends DomainException {

    public StorageUnavailableException(Throwable cause) {
        super(ErrorCode.STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                Message.STORAGE_UNAVAILABLE, cause);
    }
}
