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
    STORAGE_UNAVAILABLE
}
