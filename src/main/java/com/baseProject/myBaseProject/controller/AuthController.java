package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.dto.auth.AuthResponse;
import com.baseProject.myBaseProject.dto.auth.AuthResult;
import com.baseProject.myBaseProject.dto.auth.LoginRequest;
import com.baseProject.myBaseProject.dto.auth.RegisterRequest;
import com.baseProject.myBaseProject.exception.ApiError;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.InvalidRefreshTokenException;
import com.baseProject.myBaseProject.exception.MissingRefreshTokenException;
import com.baseProject.myBaseProject.security.RefreshTokenCookieFactory;
import com.baseProject.myBaseProject.security.SecurityUtils;
import com.baseProject.myBaseProject.security.authorization.IsAuthenticated;
import com.baseProject.myBaseProject.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;
    private final Clock clock;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = cookieFactory.read(request)
                .orElseThrow(MissingRefreshTokenException::new);

        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        cookieFactory.read(request).ifPresent(authService::logout);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @PostMapping("/logout-all")
    @IsAuthenticated
    public ResponseEntity<Void> logoutAll() {
        SecurityUtils.currentUserId().ifPresent(authService::logoutAll);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .build();
    }

    @ExceptionHandler({MissingRefreshTokenException.class, InvalidRefreshTokenException.class})
    public ResponseEntity<ApiError> handleRefreshTokenRejected(DomainException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(
                clock.instant(),
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(ex.getStatus())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
                .body(body);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.build(result.refreshToken()).toString())
                .body(result.body());
    }
}
