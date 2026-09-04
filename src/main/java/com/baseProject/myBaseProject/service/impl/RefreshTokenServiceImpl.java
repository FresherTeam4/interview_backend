package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.RefreshTokenProperties;
import com.baseProject.myBaseProject.entity.RefreshToken;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.repository.RefreshTokenRepository;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public String issue(UserAccount user) {
        return persist(user, UUID.randomUUID().toString(), clock.instant());
    }

    @Override
    @Transactional
    public RotationResult rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (stored.isRevoked()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (stored.isExpiredAt(now)) {
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserAccount user = stored.getUser();
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            throw new DomainException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        stored.setRevokedAt(now);
        String newRefreshtoken = persist(user, stored.getFamilyId(), now);

        return new RotationResult(user, newRefreshtoken);
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Override
    @Transactional
    public int revokeAllForUser(Long userId) {
        return refreshTokenRepository.revokeAllByUserId(userId, clock.instant());
    }

    @Override
    @Transactional
    public int purgeExpired() {
        return refreshTokenRepository.deleteAllExpiredBefore(clock.instant());
    }

    private String persist(UserAccount user, String familyId, Instant now) {
        String rawToken = generateRawToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .familyId(familyId)
                .user(user)
                .issuedAt(now)
                .expiresAt(now.plusMillis(properties.expirationMs()))
                .build());

        return rawToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " is required but not available", e);
        }
    }
}
