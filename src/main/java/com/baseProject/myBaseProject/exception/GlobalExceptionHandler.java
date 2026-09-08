package com.baseProject.myBaseProject.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final Clock clock;

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException ex, HttpServletRequest req) {
        log.debug("Domain error {} on {} {}: {}",
                ex.getCode(), req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(ex.getCode(), ex.getMessage(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        log.warn("Data integrity violation on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(ErrorCode.DATA_CONSTRAINT_VIOLATION, req);
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiError> handleBadCredentials(
            AuthenticationException ex, HttpServletRequest req) {
        return build(ErrorCode.INVALID_CREDENTIALS, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest req) {
        return validationError(ex.getBindingResult(), req);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest req) {
        return validationError(ex.getBindingResult(), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        return build(ErrorCode.MALFORMED_REQUEST, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return build(ErrorCode.MALFORMED_REQUEST,
                "Parameter '%s' has an invalid value".formatted(ex.getName()), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return build(ErrorCode.MALFORMED_REQUEST,
                "Required parameter '%s' is missing".formatted(ex.getParameterName()), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize(MaxUploadSizeExceededException ex,
                                                        HttpServletRequest req) {
        return build(ErrorCode.UPLOAD_TOO_LARGE, req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest req) {
        return build(ErrorCode.ENDPOINT_NOT_FOUND, req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest req) {
        return build(ErrorCode.METHOD_NOT_ALLOWED,
                "Method %s is not supported for this endpoint".formatted(ex.getMethod()), req);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest req) {
        return build(ErrorCode.ACCOUNT_DISABLED, req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                         HttpServletRequest req) {
        return build(ErrorCode.AUTHENTICATION_REQUIRED, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest req) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean anonymous = authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;

        return anonymous
                ? build(ErrorCode.AUTHENTICATION_REQUIRED, req)
                : build(ErrorCode.ACCESS_DENIED, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, req);
    }

    private ResponseEntity<ApiError> validationError(BindingResult bindingResult,
                                                     HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : bindingResult.getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getDefaultMessage(),
                fields.isEmpty() ? null : fields,
                req);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, HttpServletRequest req) {
        return build(code, code.getDefaultMessage(), req);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message,
                                           HttpServletRequest req) {
        return build(code, message, null, req);
    }

    private ResponseEntity<ApiError> build(
            ErrorCode code,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest req) {
        return ResponseEntity.status(code.getStatus())
                .body(new ApiError(
                        clock.instant(),
                        code.getStatus().value(),
                        code,
                        message,
                        req.getRequestURI(),
                        fieldErrors));
    }
}
