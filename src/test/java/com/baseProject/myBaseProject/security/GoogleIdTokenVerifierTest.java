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
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
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
    private static final String KEY_ID = "test-key-1";
    private static final String GOOGLE_SUB = "google-user-12345";
    private static final String EMAIL = "developer@example.com";
    private static final String NAME = "Fresher Developer";
    private static final String PICTURE = "https://example.com/avatar.png";

    private static RSAKey rsaKey;
    private static HttpServer jwksServer;
    private static GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void startJwksServer() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        String jwksResponse = "{\"keys\": [" + rsaKey.toPublicJWK().toJSONString() + "]}";

        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/certs", exchange -> {
            byte[] bytes = jwksResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        jwksServer.start();

        int port = jwksServer.getAddress().getPort();
        String jwkSetUri = "http://localhost:" + port + "/certs";

        GoogleOAuthProperties properties = new GoogleOAuthProperties(CLIENT_ID, jwkSetUri, List.of(ISSUER));
        verifier = new GoogleIdTokenVerifier(properties);
    }

    @AfterAll
    static void stopJwksServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    /** Token hợp lệ từ Google phải trích xuất đúng thông tin user. */
    @Test
    void acceptsValidToken() throws Exception {
        String token = signedToken(validClaims().build());

        GoogleUserInfo info = verifier.verify(token);

        assertThat(info.googleId()).isEqualTo(GOOGLE_SUB);
        assertThat(info.email()).isEqualTo(EMAIL);
        assertThat(info.fullName()).isEqualTo(NAME);
        assertThat(info.avatarUrl()).isEqualTo(PICTURE);
    }

    /**
     * Token được cấp cho một client ID khác (app khác của bên thứ ba) — trường hợp giả mạo
     * nguy hiểm nhất: chữ ký, issuer và hạn dùng đều hợp lệ.
     */
    @Test
    void rejectsTokenIssuedForAnotherClient() throws Exception {
        String token = signedToken(validClaims()
                .audience("attacker-app.apps.googleusercontent.com").build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void rejectsTokenSignedByUnknownKey() throws Exception {
        RSAKey rogueKey = new RSAKeyGenerator(2048).keyID("rogue-key").generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rogueKey.getKeyID()).build(),
                validClaims().build());
        jwt.sign(new RSASSASigner(rogueKey));

        assertThatThrownBy(() -> verifier.verify(jwt.serialize()))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(7200);
        String token = signedToken(validClaims()
                .issueTime(Date.from(past))
                .expirationTime(Date.from(past.plusSeconds(3600)))
                .build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void rejectsTokenFromUntrustedIssuer() throws Exception {
        String token = signedToken(validClaims().issuer("https://evil-idp.example.com").build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void rejectsUnverifiedEmail() throws Exception {
        String token = signedToken(validClaims().claim("email_verified", false).build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void rejectsTokenWithoutEmailClaim() throws Exception {
        String token = signedToken(validClaims().claim("email", null).build());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.INVALID_GOOGLE_TOKEN);
    }

    /** Chưa cấu hình client ID thì phải báo 503, không được âm thầm bỏ qua kiểm tra audience. */
    @Test
    void failsFastWhenClientIdMissing() throws Exception {
        GoogleIdTokenVerifier unconfigured = new GoogleIdTokenVerifier(
                new GoogleOAuthProperties("", "https://example.com/certs", List.of(ISSUER)));

        assertThatThrownBy(() -> unconfigured.verify(signedToken(validClaims().build())))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED);
    }

    private JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .subject(GOOGLE_SUB)
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .claim("email", EMAIL)
                .claim("email_verified", true)
                .claim("name", NAME)
                .claim("picture", PICTURE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
    }

    private String signedToken(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                claims);
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
