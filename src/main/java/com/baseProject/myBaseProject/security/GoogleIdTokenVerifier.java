package com.baseProject.myBaseProject.security;

import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baseProject.myBaseProject.config.properites.GoogleOAuthProperties;
import com.baseProject.myBaseProject.constant.Message;
import com.baseProject.myBaseProject.dto.auth.GoogleUserInfo;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Component
public class GoogleIdTokenVerifier {
    private final GoogleOAuthProperties properties;
    private final JwtDecoder jwtDecoder;

    public GoogleIdTokenVerifier(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.jwtDecoder = properties.configured() ? buildDecoder(properties) : null;
    }

    private static JwtDecoder buildDecoder(GoogleOAuthProperties properties) {
        // setup decoder for google token
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(properties.jwkSetUri())
                .build();


        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(), // check expried token
                issuerValidator(properties.issuers()),
                audienceValidator(properties.clientId())
        )));
        return decoder;
    }

    // get token data
    private static OAuth2TokenValidator<Jwt> issuerValidator(List<String> issuers) {
        return new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                iss -> iss != null && issuers.contains(iss.toString()));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(clientId));
    }

    public GoogleUserInfo verify(String idToken) {
        if (jwtDecoder == null) {
            throw new DomainException(ErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED);
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            throw new DomainException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        return extractUserInfo(jwt);
    }

    private GoogleUserInfo extractUserInfo(Jwt jwt) {
        String googleId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (!StringUtils.hasText(googleId) || !StringUtils.hasText(email)) {
            throw new DomainException(ErrorCode.INVALID_GOOGLE_TOKEN);
        }

        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new DomainException(ErrorCode.INVALID_GOOGLE_TOKEN, Message.GOOGLE_EMAIL_NOT_VERIFIED);
        }

        String normalizedEmail = email.trim().toLowerCase();
        String name = jwt.getClaimAsString("name");

        return new GoogleUserInfo(
                googleId,
                normalizedEmail,
                StringUtils.hasText(name) ? name.trim() : normalizedEmail,
                jwt.getClaimAsString("picture")
        );
    }
}
