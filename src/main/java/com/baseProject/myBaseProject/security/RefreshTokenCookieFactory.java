package com.baseProject.myBaseProject.security;

import com.baseProject.myBaseProject.config.properites.RefreshTokenProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Đóng gói mọi thao tác với cookie chứa refresh token.
 *
 * <p>Cookie luôn {@code HttpOnly} nên JavaScript không đọc được — đây là điểm khiến
 * refresh token an toàn trước XSS, khác với access token vốn nằm trong body JSON.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {
    private final RefreshTokenProperties properties;

    public ResponseCookie build(String rawToken) {
        return baseCookie(rawToken)
                .maxAge(Duration.ofMillis(properties.expirationMs()))
                .build();
    }

    /** Cookie rỗng hết hạn ngay, dùng khi logout hoặc khi token bị từ chối. */
    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(properties.cookiePath());
    }
}
