package com.baseProject.myBaseProject.exception;

import com.baseProject.myBaseProject.constant.Message;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j // auto create an instance of a logger
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final Clock clock;

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException ex, HttpServletRequest req) {
        log.debug("Domain error {} on {} {}: {}",
                ex.getCode(), req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), req);
    }

    // handler database error when modify database data
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        log.warn("Data integrity violation on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.CONFLICT, ErrorCode.DATA_CONSTRAINT_VIOLATION,
                Message.CONSTRAINT_VIOLATION, req);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiError> handleBadCredentials(org.springframework.security.core.AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED,ErrorCode.INVALID_CREDENTIALS ,
                Message.INVALID_CREDENTIALS, req);
    }

    // REQUEST VALIDATE DATA EXCEPTIONS

    /** argument in body not valid with condition */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest req) {
        return validationError(ex.getBindingResult(), req);
    }

    /** {@code @Valid @ModelAttribute}*/
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest req) {
        return validationError(ex.getBindingResult(), req);
    }

    /** JSON gửi lên sai cú pháp, hoặc sai kiểu dữ liệu*/
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                Message.MALFORMED_JSON, req);
    }

    /** VD: {@code GET /api/events/abc} trong khi id kiểu Long. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Parameter '%s' has an invalid value".formatted(ex.getName()), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Required parameter '%s' is missing".formatted(ex.getParameterName()), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                        HttpServletRequest req) {
        return build(HttpStatus.CONTENT_TOO_LARGE, ErrorCode.UPLOAD_TOO_LARGE,
                Message.UPLOAD_TOO_LARGE, req);
    }

    // request path not found. vd path /api...
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND,
                Message.ENDPOINT_NOT_FOUND, req);
    }

    // request method not found. vd GET /api...
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "Method %s is not supported for this endpoint".formatted(ex.getMethod()), req);
    }

    // EXCEPTION FROM SPRING SECURITY
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.ACCOUNT_DISABLED,
                Message.ACCOUNT_DISABLED, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED,
                Message.AUTHENTICATION_REQUIRED, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;

        return anonymous
                ? build(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED,
                Message.AUTHENTICATION_REQUIRED, req)
                : build(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                Message.ACCESS_DENIED, req);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                Message.INTERNAL_ERROR, req);
    }

    // helpers
    private ResponseEntity<ApiError> validationError(BindingResult bindingResult,
                                                     HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return validationResponse(fields, req);
    }


    private ResponseEntity<ApiError> validationResponse(Map<String, String> fields,
                                                        HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ApiError(
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED,
                Message.VALIDATION_FAILED,
                req.getRequestURI(),
                fields.isEmpty() ? null : fields
        ));
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                           HttpServletRequest req) {
        return ResponseEntity.status(status)
                .body(ApiError.of(clock.instant(), status.value(), code, message, req.getRequestURI()));
    }
}
