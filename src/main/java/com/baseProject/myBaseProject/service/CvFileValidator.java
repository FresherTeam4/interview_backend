package com.baseProject.myBaseProject.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Chốt kiểm file CV trước khi nó được ghi vào storage hay gửi cho AI.
 *
 * <p>Tách khỏi service nghiệp vụ vì đây là phần duy nhất phụ thuộc PDFBox, và là phần cần test
 * kỹ nhất: mỗi nhánh lỗi tương ứng một mã lỗi khác nhau ở mục 8 của
 * {@code docs/cv-profile-api.md}.
 */
public interface CvFileValidator {

    /**
     * Kiểm mọi điều kiện rồi trả về nội dung file.
     *
     * <p><strong>Trả về {@code byte[]} là cố ý.</strong> Một lần upload cần nội dung file ba lần:
     * tính SHA-256 để nhận ra file trùng, ghi lên storage, và gửi cho Gemini. Đọc
     * {@code MultipartFile} nhiều lần thì phải mở lại stream, mà sau khi request kết thúc Tomcat
     * đã xóa file tạm — trong khi việc bóc tách chạy nền lại diễn ra <em>sau</em> lúc đó. Đọc một
     * lần, giữ trong bộ nhớ, mọi tầng dùng cùng một mảng byte.
     *
     * <p>Thứ tự kiểm là một phần của hợp đồng: rẻ trước, đắt sau — rỗng, dung lượng, đuôi file,
     * 4 byte đầu, rồi mới mở bằng PDFBox. Nhờ vậy một file 5MB sai đuôi bị chặn mà không tốn
     * lượt phân tích cấu trúc PDF nào.
     *
     * @throws com.baseProject.myBaseProject.exception.CvFileRequiredException    thiếu file hoặc 0 byte
     * @throws com.baseProject.myBaseProject.exception.CvFileTooLargeException    vượt {@code app.cv.max-file-size-bytes}
     * @throws com.baseProject.myBaseProject.exception.CvInvalidFileTypeException không phải PDF
     * @throws com.baseProject.myBaseProject.exception.CvFileCorruptedException   không mở được, hoặc có mật khẩu
     * @throws com.baseProject.myBaseProject.exception.CvTooManyPagesException    vượt {@code app.cv.max-pages}
     */
    byte[] validateAndRead(MultipartFile file);
}
