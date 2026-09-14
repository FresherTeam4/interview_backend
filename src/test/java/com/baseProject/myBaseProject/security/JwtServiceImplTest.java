package com.baseProject.myBaseProject.security;

import com.baseProject.myBaseProject.config.properites.JwtProperties;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceImplTest {
    private final JwtServiceImpl service = new JwtServiceImpl(new JwtProperties(
            "a-test-secret-that-is-at-least-32-characters-long",
            3_600_000));

    @Test
    void rejectsExistingAccessTokenAfterAccountIsDisabled() {
        CustomUserDetails enabled = details(true);
        String token = service.generateAccessToken(enabled);

        assertThat(service.isTokenValid(token, enabled)).isTrue();
        assertThat(service.isTokenValid(token, details(false))).isFalse();
    }

    private CustomUserDetails details(boolean enabled) {
        return new CustomUserDetails(UserAccount.builder()
                .id(7L)
                .email("user@example.com")
                .passwordHash("password")
                .role(UserRole.USER)
                .enabled(enabled)
                .build());
    }
}
