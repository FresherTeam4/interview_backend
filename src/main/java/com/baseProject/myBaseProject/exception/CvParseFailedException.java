package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;

/**
 * Một lần bóc tách CV thất bại.
 *
 * <p><strong>Cố ý không kế thừa {@link DomainException}.</strong> Bóc tách chạy nền, sau khi
 * request upload đã trả 202 và kết thúc: không còn response nào để gắn mã lỗi HTTP vào, nên
 * {@code GlobalExceptionHandler} không bao giờ thấy lớp này. Nơi người dùng đọc được lý do là
 * cột {@code cv_documents.status_message} (mục 8 của {@code docs/cv-profile-api.md}).
 *
 * <p>Vì thế lớp này mang <em>hai</em> câu chữ, tách bạch:
 * <ul>
 *   <li>{@link #getStatusMessage()} — câu tiếng Việt ghi vào DB cho người dùng đọc;</li>
 *   <li>{@link #getMessage()} — chi tiết kỹ thuật chỉ dành cho log (mã HTTP Gemini trả về,
 *       trường nào thiếu trong JSON...). Không bao giờ đưa vào DB, vì nó có thể chứa thông tin
 *       nội bộ và không giúp gì cho người đang chờ CV của mình.</li>
 * </ul>
 *
 * <p>Chỉ tạo bằng các factory tĩnh dưới đây: mỗi factory ứng với đúng một hằng
 * {@code Message.PARSE_FAILED_*}, nên tập lý do thất bại là một danh sách đóng, đọc một chỗ là
 * biết hết.
 */
public class CvParseFailedException extends RuntimeException {

    private final String statusMessage;

    private CvParseFailedException(String statusMessage, String technicalDetail, Throwable cause) {
        super(technicalDetail, cause);
        this.statusMessage = statusMessage;
    }

    /** Câu ghi vào {@code cv_documents.status_message}. */
    public String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Chưa cấu hình {@code app.ai.api-key}.
     *
     * <p>Kiểm trước khi gọi mạng: thiếu khóa thì Gemini trả 400/403 và ta phải đoán lý do, còn
     * chặn ở đây thì thông báo nói thẳng là lỗi cấu hình máy chủ chứ không phải lỗi file CV.
     */
    public static CvParseFailedException noApiKey() {
        return new CvParseFailedException(Message.PARSE_FAILED_NO_API_KEY,
                "Thiếu cấu hình app.ai.api-key", null);
    }

    /** Gemini không trả lời trong {@code app.ai.timeout-ms}. */
    public static CvParseFailedException timeout(Throwable cause) {
        return new CvParseFailedException(Message.PARSE_FAILED_TIMEOUT,
                "Gemini không phản hồi trong thời gian chờ", cause);
    }

    /**
     * Gọi được Gemini nhưng nội dung trả về không dùng được: không phải JSON, thiếu
     * {@code model_output}, hoặc JSON không khớp schema.
     *
     * <p>Khác {@link #unexpected(Throwable)}: ở đây request của mình hợp lệ, chỉ là kết quả
     * không như hợp đồng — thử lại thường có kết quả khác vì mô hình sinh lại từ đầu.
     */
    public static CvParseFailedException badResponse(String technicalDetail) {
        return badResponse(technicalDetail, null);
    }

    public static CvParseFailedException badResponse(String technicalDetail, Throwable cause) {
        return new CvParseFailedException(Message.PARSE_FAILED_BAD_RESPONSE,
                "Phản hồi Gemini không dùng được: " + technicalDetail, cause);
    }

    /** Gemini trả 5xx/429, hoặc không nối được tới nó. Lỗi phía họ, thử lại sau là hợp lý. */
    public static CvParseFailedException aiUnavailable(String technicalDetail, Throwable cause) {
        return new CvParseFailedException(Message.PARSE_FAILED_AI_UNAVAILABLE,
                "Gemini không khả dụng: " + technicalDetail, cause);
    }

    /**
     * Không tải lại được file từ storage để gửi cho AI.
     *
     * <p>Xảy ra khi MinIO chết trong khoảng giữa lúc upload xong và lúc task nền chạy.
     */
    public static CvParseFailedException fileUnreadable(Throwable cause) {
        return new CvParseFailedException(Message.PARSE_FAILED_FILE_UNREADABLE,
                "Không đọc lại được file CV từ storage", cause);
    }

    /**
     * Hàng đợi bóc tách đầy — {@code TaskRejectedException} ném ra ngay tại luồng gọi
     * {@code @Async}, tức là vẫn còn trong request upload.
     *
     * <p>Không phải lỗi của file, cũng không phải lỗi của Gemini: chỉ là 4 luồng đang chạy và
     * 20 chỗ chờ đã kín. Ghi thành {@code FAILED} thay vì để CV nằm mãi ở {@code UPLOADED},
     * vì không có task nào sẽ nhặt nó lên nữa; người dùng bấm bóc tách lại là xong.
     */
    public static CvParseFailedException queueFull(Throwable cause) {
        return new CvParseFailedException(Message.PARSE_FAILED_QUEUE_FULL,
                "Hàng đợi bóc tách CV đã đầy", cause);
    }

    /**
     * Mọi thứ còn lại — kể cả 4xx từ Gemini.
     *
     * <p>4xx nghĩa là request của <em>mình</em> sai (sai shape, sai model, khóa hết hạn): người
     * dùng thử lại bao nhiêu lần cũng vẫn 4xx, nên chỗ ném phải ghi log mức error để lập trình
     * viên sửa, còn người dùng chỉ nhận một câu chung.
     */
    public static CvParseFailedException unexpected(Throwable cause) {
        return new CvParseFailedException(Message.PARSE_FAILED_UNEXPECTED,
                "Lỗi ngoài dự kiến khi bóc tách CV", cause);
    }
}
