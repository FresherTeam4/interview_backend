package com.baseProject.myBaseProject.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.baseProject.myBaseProject.config.properites.GoogleOAuthProperties;
import com.baseProject.myBaseProject.dto.auth.GoogleUserInfo;
import com.baseProject.myBaseProject.exception.GoogleLoginNotConfiguredException;
import com.baseProject.myBaseProject.exception.InvalidGoogleTokenException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Kiểm tra {@link GoogleIdTokenVerifier} bằng cách tự sinh cặp khoá RSA và ký token,
 * còn JWKS thì phục vụ qua một HTTP server nội bộ — test chạy được khi offline mà vẫn
 * đi đúng đường tải khoá như lúc chạy thật.
 *
 * <p>Trọng tâm là các trường hợp token <b>phải bị từ chối</b>: đó mới là phần bảo vệ
 * hệ thống. Một verifier chấp nhận đúng token hợp lệ nhưng không chặn token sai
 * audience thì vẫn là lỗ hổng chiếm tài khoản. Test đường hợp lệ ở đây còn có vai trò
 * chứng minh các test từ chối không "đậu giả" vì JWKS tải thất bại.
 */
class GoogleIdTokenVerifierTest {
    private static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_SUB = "1234567890";
    private static final String EMAIL = "nguoidung@gmail.com";

    private static RSAKey signingKey;
    private static GoogleIdTokenVerifier verifier;
    private static HttpServer jwkServer;

    @BeforeAll
    static void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        byte[] jwkSet = ("{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}")
                .getBytes(StandardCharsets.UTF_8);

        // Port 0 để OS cấp cổng rỗi, tránh xung đột khi chạy song song trên CI.
        jwkServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwkServer.createContext("/certs", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwkSet.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(jwkSet);
            }
        });
        jwkServer.start();

        String jwkSetUri = "http://127.0.0.1:" + jwkServer.getAddress().getPort() + "/certs";
        verifier = new GoogleIdTokenVerifier(new GoogleOAuthProperties(
                CLIENT_ID, jwkSetUri, List.of(ISSUER, "accounts.google.com")));
    }

    @AfterAll
    static void tearDown() {
        jwkServer.stop(0);
    }

    @Test
    void acceptsValidTokenAndExtractsProfile() throws Exception {
        GoogleUserInfo info = verifier.verify(signedToken(validClaims().build()));

        assertThat(info.googleId()).isEqualTo(GOOGLE_SUB);
        assertThat(info.email()).isEqualTo(EMAIL);
        assertThat(info.fullName()).isEqualTo("Nguyen Van A");
        assertThat(info.avatarUrl()).isEqualTo("https://lh3.googleusercontent.com/a/photo");
    }

    @Test
    void normalizesUppercaseEmailToLowercase() throws Exception {
        GoogleUserInfo info = verifier.verify(signedToken(
                validClaims().claim("email", "Nguoi.Dung@Gmail.COM").build()));

        assertThat(info.email()).isEqualTo("nguoi.dung@gmail.com");
    }

    @Test
    void fallsBackToEmailWhenNameClaimAbsent() throws Exception {
        GoogleUserInfo info = verifier.verify(signedToken(
                validClaims().claim("name", null).build()));

        assertThat(info.fullName()).isEqualTo(EMAIL);
    }

    /**
     * Token do Google ký thật nhưng phát cho ứng dụng khác. Đây là kịch bản tấn công
     * nguy hiểm nhất: chữ ký, issuer và hạn dùng đều hợp lệ.
     */
    @Test
    void rejectsTokenIssuedForAnotherClient() throws Exception {
        String token = signedToken(validClaims()
                .audience("attacker-app.apps.googleusercontent.com").build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsTokenSignedByUnknownKey() throws Exception {
        RSAKey rogueKey = new RSAKeyGenerator(2048).keyID("rogue-key").generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rogueKey.getKeyID()).build(),
                validClaims().build());
        jwt.sign(new RSASSASigner(rogueKey));

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(7200);
        String token = signedToken(validClaims()
                .issueTime(Date.from(past))
                .expirationTime(Date.from(past.plusSeconds(3600)))
                .build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsTokenFromUntrustedIssuer() throws Exception {
        String token = signedToken(validClaims().issuer("https://evil-idp.example.com").build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsUnverifiedEmail() throws Exception {
        String token = signedToken(validClaims().claim("email_verified", false).build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsTokenWithoutEmailClaim() throws Exception {
        String token = signedToken(validClaims().claim("email", null).build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    /** Chưa cấu hình client ID thì phải báo 503, không được âm thầm bỏ qua kiểm tra audience. */
    @Test
    void failsFastWhenClientIdMissing() throws Exception {
        GoogleIdTokenVerifier unconfigured = new GoogleIdTokenVerifier(
                new GoogleOAuthProperties("", "https://example.com/certs", List.of(ISSUER)));

        assertThatThrownBy(() -> unconfigured.verify(signedToken(validClaims().build())))
                .isInstanceOf(GoogleLoginNotConfiguredException.class);
    }

    private JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(GOOGLE_SUB)
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", EMAIL)
                .claim("email_verified", true)
                .claim("name", "Nguyen Van A")
                .claim("picture", "https://lh3.googleusercontent.com/a/photo");
    }

    private String signedToken(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
