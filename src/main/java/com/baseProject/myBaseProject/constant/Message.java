package com.baseProject.myBaseProject.constant;

public final class Message {
    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String AUTHENTICATION_REQUIRED = "Authentication is required to access this resource";
    public static final String ACCESS_DENIED = "You do not have permission to access this resource";
    public static final String ACCOUNT_DISABLED = "This account has been disabled";
    public static final String MALFORMED_JSON = "Malformed JSON request body";
    public static final String VALIDATION_FAILED = "Validation failed";
    public static final String CONSTRAINT_VIOLATION = "Resource already exists or violates a data constraint";
    public static final String ENDPOINT_NOT_FOUND = "No endpoint found for this request";
    public static final String INTERNAL_ERROR = "Internal server error";
    public static final String UPLOAD_TOO_LARGE = "File tải lên vượt quá dung lượng cho phép";
    public static final String EMAIL_NOT_FOUND = "Email not found";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String MISSING_REFRESH_TOKEN = "Refresh token cookie is missing";
    public static final String INVALID_GOOGLE_TOKEN = "Google ID token is invalid or has expired";
    public static final String GOOGLE_EMAIL_NOT_VERIFIED = "This Google account has no verified email";
    public static final String GOOGLE_LOGIN_NOT_CONFIGURED =
            "Google login is not configured on this server";

    // ---- CV & Hồ sơ ----
    // Nhóm này viết tiếng Việt, khác các dòng trên. Đây là thông báo người dùng cuối đọc
    // trực tiếp trên màn hình upload CV, còn nhóm auth ở trên là lỗi kỹ thuật frontend xử lý.
    // Mấy chuỗi có %s/%d là template, chỗ ném exception tự .formatted(...).

    public static final String CV_FILE_REQUIRED = "Chưa chọn file CV, hoặc file rỗng";
    public static final String CV_INVALID_FILE_TYPE = "Chỉ nhận file PDF";
    /** Dùng cho lưới an toàn ở tầng servlet, chỗ không biết hạn mức của app là bao nhiêu. */
    public static final String CV_FILE_TOO_LARGE = "File CV vượt quá dung lượng cho phép";
    public static final String CV_FILE_TOO_LARGE_LIMIT =
            "File CV vượt quá dung lượng cho phép, tối đa %d MB";
    public static final String CV_FILE_CORRUPTED =
            "Không mở được file PDF này, hoặc file đang được đặt mật khẩu";
    public static final String CV_TOO_MANY_PAGES = "File CV có %d trang, tối đa %d trang";
    public static final String CV_LIMIT_REACHED =
            "Bạn đã giữ tối đa %d CV. Hãy xóa một CV cũ trước khi tải lên CV mới";
    public static final String CV_NOT_FOUND = "Không tìm thấy CV này";
    public static final String CV_PARSE_IN_PROGRESS =
            "CV đang được bóc tách, vui lòng đợi rồi thử lại";
    public static final String CV_PARSE_NOT_RETRYABLE =
            "Chỉ bóc tách lại được CV đang ở trạng thái thất bại";
    public static final String PROFILE_NOT_FOUND = "Không tìm thấy hồ sơ này";
    public static final String PROFILE_ITEM_NOT_FOUND = "%s id %d không thuộc hồ sơ này";
    public static final String DUPLICATE_SKILL_NAME = "Kỹ năng \"%s\" bị trùng trong danh sách";
    public static final String PROFILE_NOT_CONFIRMED =
            "Hồ sơ này chưa được xác nhận thông tin chính xác";
    public static final String STORAGE_UNAVAILABLE =
            "Hiện chưa lưu được file, vui lòng thử lại sau ít phút";

    // ---- Job Description ----
    public static final String JD_CONTENT_REQUIRED = "Nội dung JD không được để trống";
    public static final String JD_INVALID_TEXT =
            "Nội dung JD phải từ %d đến %d ký tự";
    public static final String JD_INVALID_UTF8 = "File TXT phải được mã hóa bằng UTF-8 hợp lệ";
    public static final String JD_NOT_FOUND = "Không tìm thấy JD này";
    public static final String JD_ALREADY_CONFIRMED =
            "JD đã được xác nhận; hãy tạo JD mới nếu bạn muốn thay đổi nội dung";
    public static final String JD_LIMIT_REACHED =
            "Bạn đã giữ tối đa %d JD. Hãy xóa một JD cũ trước khi tạo JD mới";
    public static final String JD_INVALID_FILE_TYPE = "Chỉ nhận file JD định dạng PDF hoặc TXT";
    public static final String JD_FILE_TOO_LARGE =
            "File JD vượt quá dung lượng cho phép, tối đa %d MB";
    public static final String JD_FILE_CORRUPTED =
            "Không đọc được file JD này, hoặc file PDF đang được đặt mật khẩu";
    public static final String JD_TOO_MANY_PAGES =
            "File JD có %d trang, tối đa %d trang";
    public static final String JD_HAS_NO_FILE = "JD dạng text không có file gốc để xem lại";
    public static final String JD_NOT_CONFIRMED =
            "JD này chưa được xác nhận để dùng cho phiên phỏng vấn";

    // ---- Interview Engine ----
    public static final String RUBRIC_NOT_AVAILABLE =
            "Rubric phỏng vấn hiện chưa sẵn sàng, vui lòng thử lại sau";
    public static final String SESSION_NOT_FOUND = "Không tìm thấy phiên phỏng vấn này";
    public static final String SESSION_INVALID_STATE =
            "Thao tác này không hợp lệ với trạng thái hiện tại của phiên phỏng vấn";
    public static final String SESSION_VERSION_CONFLICT =
            "Phiên phỏng vấn đã thay đổi; vui lòng tải lại trạng thái mới nhất";
    public static final String SESSION_LIMIT_REACHED =
            "Bạn đã có tối đa %d phiên phỏng vấn chưa kết thúc";
    public static final String SESSION_RETRY_NOT_ALLOWED =
            "Phiên phỏng vấn này hiện không có bước xử lý nào có thể thử lại";
    public static final String IDEMPOTENCY_KEY_REQUIRED =
            "Idempotency-Key là bắt buộc khi tạo phiên phỏng vấn";
    public static final String IDEMPOTENCY_KEY_REUSED =
            "Idempotency-Key này đã được dùng cho một yêu cầu tạo phiên khác";
    public static final String CLIENT_TURN_ID_REUSED =
            "clientTurnId này đã được dùng cho một câu trả lời khác";
    public static final String CURRENT_PROMPT_MISMATCH =
            "Câu trả lời không thuộc câu hỏi hiện tại của phiên phỏng vấn";
    public static final String ANSWER_REQUIRED = "Câu trả lời không được để trống";
    public static final String ANSWER_TOO_LONG =
            "Câu trả lời tối đa %d ký tự";
    public static final String INTERVIEW_AI_UNAVAILABLE =
            "Tính năng sinh câu hỏi phỏng vấn hiện chưa sẵn sàng";
    public static final String SCRIPT_GENERATION_FAILED_NO_API_KEY =
            "Máy chủ chưa cấu hình dịch vụ sinh câu hỏi phỏng vấn";
    public static final String SCRIPT_GENERATION_FAILED_TIMEOUT =
            "Sinh câu hỏi phỏng vấn quá lâu nên đã dừng; vui lòng thử lại";
    public static final String SCRIPT_GENERATION_FAILED_AI_UNAVAILABLE =
            "Dịch vụ sinh câu hỏi phỏng vấn đang không phản hồi; vui lòng thử lại sau";
    public static final String SCRIPT_GENERATION_FAILED_INVALID_OUTPUT =
            "Chưa sinh được bộ câu hỏi hợp lệ; vui lòng thử lại";
    public static final String SCRIPT_GENERATION_FAILED_UNEXPECTED =
            "Sinh câu hỏi phỏng vấn thất bại; vui lòng thử lại sau";
    public static final String FOLLOW_UP_FAILED_NO_API_KEY =
            "Máy chủ chưa cấu hình dịch vụ tạo câu hỏi đào sâu";
    public static final String FOLLOW_UP_FAILED_TIMEOUT =
            "Tạo câu hỏi tiếp theo quá lâu nên đã dừng; vui lòng thử lại";
    public static final String FOLLOW_UP_FAILED_AI_UNAVAILABLE =
            "Dịch vụ tạo câu hỏi tiếp theo đang không phản hồi; vui lòng thử lại sau";
    public static final String FOLLOW_UP_FAILED_INVALID_OUTPUT =
            "Chưa tạo được câu hỏi tiếp theo hợp lệ; vui lòng thử lại";
    public static final String FOLLOW_UP_FAILED_UNEXPECTED =
            "Tạo câu hỏi tiếp theo thất bại; vui lòng thử lại sau";

    // ---- status_message của lần bóc tách CV ----
    // Không trả qua HTTP: bóc tách chạy sau khi request đã kết thúc, nên chỗ đọc được là
    // cột status_message. Viết cho người dùng, không đổ stacktrace hay message thô của Gemini.

    public static final String PARSE_FAILED_NO_API_KEY =
            "Máy chủ chưa cấu hình khóa API để bóc tách CV, vui lòng liên hệ quản trị viên";
    public static final String PARSE_FAILED_TIMEOUT =
            "Bóc tách CV quá lâu nên đã dừng lại, bạn thử lại giúp mình";
    public static final String PARSE_FAILED_BAD_RESPONSE =
            "Kết quả bóc tách không đúng định dạng mong đợi, bạn thử lại giúp mình";
    public static final String PARSE_FAILED_AI_UNAVAILABLE =
            "Dịch vụ bóc tách CV đang không phản hồi, bạn thử lại sau ít phút";
    public static final String PARSE_FAILED_FILE_UNREADABLE =
            "Không đọc lại được file CV đã tải lên, bạn thử tải lên lại giúp mình";
    public static final String PARSE_FAILED_UNEXPECTED =
            "Bóc tách CV thất bại vì một lỗi ngoài dự kiến, bạn thử lại giúp mình";
    /**
     * Hàng đợi bóc tách đã đầy (20 chỗ, xem {@code AsyncConfig}). Khác mọi lý do còn lại:
     * file không có vấn đề gì, chỉ là máy chủ đang tắc — nên câu chữ nói rõ "thử lại ngay".
     */
    public static final String PARSE_FAILED_QUEUE_FULL =
            "Máy chủ đang xử lý quá nhiều CV cùng lúc, bạn bấm bóc tách lại sau một chút giúp mình";
    public static final String PARSE_FAILED_INTERRUPTED_BY_RESTART =
            "Lần bóc tách trước bị dừng giữa lúc máy chủ khởi động lại, bạn bấm thử lại giúp mình";
}
