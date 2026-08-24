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
    public static final String EMAIL_NOT_FOUND = "Email not found";
    public static final String USER_NOT_FOUND = "User not found";
    public static final String MISSING_REFRESH_TOKEN = "Refresh token cookie is missing";
    public static final String INVALID_GOOGLE_TOKEN = "Google ID token is invalid or has expired";
    public static final String GOOGLE_EMAIL_NOT_VERIFIED = "This Google account has no verified email";
    public static final String GOOGLE_LOGIN_NOT_CONFIGURED =
            "Google login is not configured on this server";

    // ---- CV & hồ sơ ứng viên ----
    public static final String CV_NOT_FOUND = "CV không tồn tại";
    public static final String CV_NOT_ACTIVE =
            "Chỉ có thể bóc tách CV đang được sử dụng (active). Hãy tải lên CV mới nếu muốn phân tích lại";
    public static final String CV_FILE_REQUIRED = "Vui lòng chọn file CV";
    public static final String CV_MUST_BE_PDF = "Chỉ nhận file CV định dạng PDF";
    public static final String CV_FILE_TOO_LARGE = "File CV vượt quá dung lượng cho phép (tối đa %s MB)";
    public static final String CV_ALREADY_PARSED =
            "CV này đã được bóc tách. Hãy tải lên CV mới nếu muốn phân tích lại";
    public static final String CV_STORAGE_FAILED = "Không lưu/đọc được file CV trên server";
    public static final String CV_PARSE_FAILED = "Bóc tách CV thất bại, vui lòng thử lại";
    public static final String CV_PARSE_EMPTY_RESULT = "AI không trả về dữ liệu hồ sơ nào từ CV này";
    public static final String CV_PARSE_NOT_CONFIGURED =
            "Server chưa cấu hình khóa API cho dịch vụ AI bóc tách CV";
    public static final String PROFILE_NOT_FOUND =
            "Chưa có hồ sơ ứng viên. Hãy tải lên CV và bóc tách trước";
    public static final String PROFILE_EDUCATION_YEARS_INVALID =
            "Học vấn \"%s\": năm kết thúc phải lớn hơn hoặc bằng năm bắt đầu";
    public static final String PROFILE_PROJECT_DATES_INVALID =
            "Dự án \"%s\": ngày kết thúc phải sau hoặc bằng ngày bắt đầu";
}
