package com.baseProject.myBaseProject.constant;

public final class Message {
    public static final String TEMPLATE_NOT_FOUND = "Interview template not found";
    public static final String TEMPLATE_VERSION_CONFLICT = "Interview template changed; reload it and try again";
    public static final String TEMPLATE_ARCHIVED = "Interview template is archived";
    public static final String TEMPLATE_INVALID_ANALYSIS = "Job analysis did not match the required contract";
    public static final String TEMPLATE_INSUFFICIENT_JD = "Please provide a job description with responsibilities or job requirements";
    public static final String TEMPLATE_ALREADY_CONFIRMED = "Confirmed interview templates cannot be edited";
    public static final String TEMPLATE_CONFIRM_REQUIRED = "Confirm the interview template before using or publishing it";

    // Job description processing
    public static final String JD_FILE_REQUIRED = "Job description file is required";
    public static final String JD_INVALID_FILE_TYPE = "Job description must be a valid PDF file";
    public static final String JD_FILE_TOO_LARGE = "Job description file exceeds the allowed size";
    public static final String JD_FILE_CORRUPTED = "Job description file is corrupted, encrypted, or unreadable";
    public static final String JD_TOO_MANY_PAGES = "Job description has too many pages";
    public static final String JD_TEXT_TOO_LONG = "Job description text exceeds the allowed length";
    public static final String JD_EMPTY_TEXT = "Job description contains no extractable text";
    public static final String JD_LIMIT_REACHED = "Maximum number of job descriptions reached";
    public static final String JD_NOT_FOUND = "Job description not found";
    public static final String JD_PROCESSING_IN_PROGRESS = "Job description processing is already in progress";
    public static final String JD_PROCESSING_NOT_RETRYABLE = "Only failed job description processing can be retried";
    public static final String JD_FILE_NOT_AVAILABLE = "This job description was created from text and has no file";
    public static final String JD_PROCESSING_FAILED = "Failed to process job description";
    public static final String JD_ANALYSIS_NOT_READY = "Job description analysis is not ready";

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

    // AI & CV parsing
    public static final String AI_SERVICE_UNAVAILABLE = "AI service is temporarily unavailable";
    public static final String AI_TIMEOUT = "AI service request timed out";
    public static final String AI_MALFORMED_OUTPUT = "AI service returned invalid or unparseable output";
    public static final String AI_CONFIG_ERROR = "AI service is not configured properly";
    public static final String AI_ERROR = "AI service execution failed";
    public static final String CV_PARSE_FAILED = "Failed to parse CV content";

    // CV upload
    public static final String UPLOAD_TOO_LARGE = "Uploaded request is too large";
    public static final String CV_FILE_REQUIRED = "CV file is required";
    public static final String CV_INVALID_FILE_TYPE = "CV must be a valid PDF file";
    public static final String CV_FILE_TOO_LARGE = "CV file exceeds the allowed size";
    public static final String CV_FILE_CORRUPTED = "CV file is corrupted, encrypted, or unreadable";
    public static final String CV_TOO_MANY_PAGES = "CV has too many pages";
    public static final String CV_LIMIT_REACHED = "Maximum number of CVs reached";
    public static final String CV_NOT_FOUND = "CV not found";
    public static final String CV_PARSE_IN_PROGRESS = "CV parsing is already in progress";
    public static final String CV_PARSE_NOT_RETRYABLE = "Only failed CV parsing can be retried";
    public static final String STORAGE_UNAVAILABLE = "File storage is temporarily unavailable";

    // Candidate profile
    public static final String PROFILE_NOT_FOUND = "Candidate profile not found";
    public static final String PROFILE_ITEM_NOT_FOUND = "Profile item not found";
    public static final String DUPLICATE_SKILL_NAME = "Profile contains duplicate skill names";
    public static final String PROFILE_VERSION_CONFLICT =
            "Candidate profile was changed by another request; reload it and try again";
}
