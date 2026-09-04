package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ---- 404 ----
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent not found"),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, Message.ENDPOINT_NOT_FOUND),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    // ---- 409 ----
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already registered"),
    DUPLICATE_REGISTRATION(HttpStatus.CONFLICT, "Duplicate registration"),
    INVALID_EVENT_STATE(HttpStatus.CONFLICT, "Invalid event state"),
    EVENT_FULL(HttpStatus.CONFLICT, "Event is full"),
    EVENT_ALREADY_STARTED(HttpStatus.CONFLICT, "Event already started"),
    REGISTRATION_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Registration already cancelled"),
    CAPACITY_BELOW_ACTIVE_REGISTRATIONS(HttpStatus.CONFLICT, "Capacity below active registrations"),
    DATA_CONSTRAINT_VIOLATION(HttpStatus.CONFLICT, Message.CONSTRAINT_VIOLATION),

    // ---- 400 ----
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, Message.VALIDATION_FAILED),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, Message.MALFORMED_JSON),

    // ---- 401 / 403 ----
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, Message.INVALID_CREDENTIALS),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, Message.AUTHENTICATION_REQUIRED),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, Message.ACCOUNT_DISABLED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, Message.ACCESS_DENIED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"),
    MISSING_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, Message.MISSING_REFRESH_TOKEN),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, Message.INVALID_GOOGLE_TOKEN),

    // ---- 405 / 500 / 503 ----
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Message.INTERNAL_ERROR),
    STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Storage operation failed"),
    GOOGLE_LOGIN_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, Message.GOOGLE_LOGIN_NOT_CONFIGURED),

    // ---- AI / CV ----
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Message.AI_SERVICE_UNAVAILABLE),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, Message.AI_TIMEOUT),
    AI_MALFORMED_OUTPUT(HttpStatus.INTERNAL_SERVER_ERROR, Message.AI_MALFORMED_OUTPUT),
    AI_CONFIG_ERROR(HttpStatus.SERVICE_UNAVAILABLE, Message.AI_CONFIG_ERROR),
    AI_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, Message.AI_ERROR),
    CV_PARSE_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, Message.CV_PARSE_FAILED),

    // ---- CV upload ----
    UPLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, Message.UPLOAD_TOO_LARGE),
    CV_FILE_REQUIRED(HttpStatus.BAD_REQUEST, Message.CV_FILE_REQUIRED),
    CV_INVALID_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, Message.CV_INVALID_FILE_TYPE),
    CV_FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, Message.CV_FILE_TOO_LARGE),
    CV_FILE_CORRUPTED(HttpStatus.BAD_REQUEST, Message.CV_FILE_CORRUPTED),
    CV_TOO_MANY_PAGES(HttpStatus.BAD_REQUEST, Message.CV_TOO_MANY_PAGES),
    CV_LIMIT_REACHED(HttpStatus.CONFLICT, Message.CV_LIMIT_REACHED),
    CV_NOT_FOUND(HttpStatus.NOT_FOUND, Message.CV_NOT_FOUND),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Message.STORAGE_UNAVAILABLE);

    private final HttpStatus status;
    private final String defaultMessage;
}
