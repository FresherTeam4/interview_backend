package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.entity.UserAccount;

public interface RefreshTokenService {
    String issue(UserAccount user);
    RotationResult rotate(String rawToken);
    void revoke(String rawToken);
    int revokeAllForUser(Long userId);
    int purgeExpired();

    record RotationResult(UserAccount user, String refreshToken) {}
}
