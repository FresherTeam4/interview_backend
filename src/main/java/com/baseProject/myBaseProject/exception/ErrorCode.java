package com.baseProject.myBaseProject.exception;

public enum ErrorCode {

    // ---- 404 ----
    AGENT_NOT_FOUND,
    ENDPOINT_NOT_FOUND,
    RESOURCE_NOT_FOUND,

    // ---- 409 ----
    DUPLICATE_EMAIL,
    DUPLICATE_REGISTRATION,
    INVALID_EVENT_STATE,
    EVENT_FULL,
    EVENT_ALREADY_STARTED,
    REGISTRATION_ALREADY_CANCELLED,
    CAPACITY_BELOW_ACTIVE_REGISTRATIONS,
    DATA_CONSTRAINT_VIOLATION,

    // ---- 400 ----
    VALIDATION_FAILED,
    MALFORMED_REQUEST,

    // ---- 401 / 403 ----
    INVALID_CREDENTIALS,
    AUTHENTICATION_REQUIRED,
    ACCOUNT_DISABLED,
    ACCESS_DENIED,
    INVALID_REFRESH_TOKEN,
    MISSING_REFRESH_TOKEN,
    INVALID_GOOGLE_TOKEN,

    // ---- 405 / 500 / 503 ----
    METHOD_NOT_ALLOWED,
    INTERNAL_ERROR,
    GOOGLE_LOGIN_NOT_CONFIGURED,
    /** 413 — lưới an toàn multipart toàn cục, trước validator theo feature. */
    UPLOAD_TOO_LARGE,

    // ---- CV & Hồ sơ ----
    // Nhóm theo tính năng thay vì theo mã HTTP như các khối trên: 14 mã này trải từ 400 tới
    // 503, xếp theo status thì rải khắp file và mất dấu tính năng nào dùng mã nào.
    /** 400 — không có field {@code file}, hoặc file rỗng 0 byte. */
    CV_FILE_REQUIRED,
    /** 415 — không phải {@code .pdf}, hoặc 4 byte đầu không phải {@code %PDF}. */
    CV_INVALID_FILE_TYPE,
    /** 413 — vượt {@code app.cv.max-file-size-bytes}. */
    CV_FILE_TOO_LARGE,
    /** 400 — PDFBox không mở được, hoặc PDF có mật khẩu. */
    CV_FILE_CORRUPTED,
    /** 400 — vượt {@code app.cv.max-pages}, gần như chắc chắn không phải CV. */
    CV_TOO_MANY_PAGES,
    /** 409 — đã giữ đủ {@code app.cv.max-per-user} CV chưa xóa. */
    CV_LIMIT_REACHED,
    /** 404 — {@code cvId} không thuộc người gọi, hoặc đã xóa mềm. */
    CV_NOT_FOUND,
    /** 409 — bóc tách lại hoặc xóa khi đang {@code PARSING}. */
    CV_PARSE_IN_PROGRESS,
    /** 409 — bóc tách lại khi {@code status} không phải {@code FAILED}. */
    CV_PARSE_NOT_RETRYABLE,
    /** 404 — {@code profileId} không thuộc người gọi, hoặc CV của nó đã xóa mềm. */
    PROFILE_NOT_FOUND,
    /** 404 — {@code id} con trong payload {@code PUT} không thuộc hồ sơ này. */
    PROFILE_ITEM_NOT_FOUND,
    /** 409 — một payload {@code PUT} chứa cả "Java" và "java". */
    DUPLICATE_SKILL_NAME,
    /** 409 — hồ sơ được chọn chưa bấm xác nhận (nhóm 4 dùng lúc tạo phiên). */
    PROFILE_NOT_CONFIRMED,
    /** 503 — không kết nối được object storage. */
    STORAGE_UNAVAILABLE,

    // ---- Job Description ----
    /** 400 — body không có nội dung JD hoặc chỉ gồm khoảng trắng. */
    JD_CONTENT_REQUIRED,
    /** 400 — nội dung JD nằm ngoài giới hạn độ dài cấu hình. */
    JD_INVALID_TEXT,
    /** 404 — JD không thuộc người gọi hoặc đã xóa mềm. */
    JD_NOT_FOUND,
    /** 409 — cố sửa một JD đã xác nhận. */
    JD_ALREADY_CONFIRMED,
    /** 409 — đã giữ đủ số JD active được cấu hình. */
    JD_LIMIT_REACHED,
    /** 415 — extension không phải PDF/TXT hoặc nội dung không khớp loại file. */
    JD_INVALID_FILE_TYPE,
    /** 413 — file vượt {@code app.jd.max-file-size-bytes}. */
    JD_FILE_TOO_LARGE,
    /** 400 — file hỏng, không đọc được hoặc PDF được mã hóa. */
    JD_FILE_CORRUPTED,
    /** 400 — PDF vượt {@code app.jd.max-pages}. */
    JD_TOO_MANY_PAGES,
    /** 409 — yêu cầu file URL cho một JD source TEXT. */
    JD_HAS_NO_FILE,
    /** 409 — JD được chọn chưa ở trạng thái READY. */
    JD_NOT_CONFIRMED,

    // ---- Interview Engine ----
    /** 503 — rubric MVP không có current published version hợp lệ. */
    RUBRIC_NOT_AVAILABLE,
    /** 404 — session không tồn tại hoặc không thuộc người gọi. */
    SESSION_NOT_FOUND,
    /** 409 — event không hợp lệ với trạng thái session hiện tại. */
    SESSION_INVALID_STATE,
    /** 409 — expectedVersion không còn là phiên bản hiện tại. */
    SESSION_VERSION_CONFLICT,
    /** 409 — user đã có đủ số session chưa kết thúc. */
    SESSION_LIMIT_REACHED,
    /** 409 — session/stage hiện tại không cho phép retry. */
    SESSION_RETRY_NOT_ALLOWED,
    /** 400 — create session thiếu hoặc gửi key chỉ có whitespace. */
    IDEMPOTENCY_KEY_REQUIRED,
    /** 409 — cùng create key/clientTurnId được dùng cho request có nội dung khác. */
    IDEMPOTENCY_KEY_REUSED,
    /** 409 — answer nhắm tới interviewer turn không còn là prompt hiện tại. */
    CURRENT_PROMPT_MISMATCH,
    /** 400 — text answer rỗng hoặc chỉ có whitespace. */
    ANSWER_REQUIRED,
    /** 400 — text answer vượt giới hạn ký tự cấu hình. */
    ANSWER_TOO_LONG,
    /** 503 — provider hoặc credential sinh nội dung phỏng vấn không dùng được. */
    INTERVIEW_AI_UNAVAILABLE,
    /** 409 — scoring chưa hoàn tất nên report chưa thể đọc. */
    REPORT_NOT_READY,
    /** 409 — scoring đã thất bại và session cần retry. */
    SESSION_RETRY_REQUIRED,

    // ---- Interview Voice ----
    /** 400 — request không có audio hoặc audio rỗng. */
    AUDIO_FILE_REQUIRED,
    /** 400 — audio/container metadata hỏng hoặc không hợp lệ. */
    AUDIO_INVALID,
    /** 413 — audio vượt giới hạn dung lượng của voice feature. */
    AUDIO_FILE_TOO_LARGE,
    /** 413 — recording vượt giới hạn thời lượng. */
    AUDIO_DURATION_TOO_LONG,
    /** 415 — container/codec audio không được hỗ trợ. */
    AUDIO_INVALID_FILE_TYPE,
    /** 404 — voice attempt không thuộc session của người gọi. */
    VOICE_ATTEMPT_NOT_FOUND,
    /** 409 — attempt không ở trạng thái cho phép edit/confirm. */
    VOICE_ATTEMPT_INVALID_STATE,
    /** 409 — optimistic version của attempt đã cũ. */
    VOICE_ATTEMPT_VERSION_CONFLICT,
    /** 400 — transcript cuối cùng rỗng. */
    TRANSCRIPT_REQUIRED,
    /** 503 — STT credential/provider không dùng được. */
    STT_UNAVAILABLE
}
