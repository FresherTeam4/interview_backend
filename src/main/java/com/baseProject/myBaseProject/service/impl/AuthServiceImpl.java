package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.dto.auth.AuthResponse;
import com.baseProject.myBaseProject.dto.auth.AuthResult;
import com.baseProject.myBaseProject.dto.auth.LoginRequest;
import com.baseProject.myBaseProject.dto.auth.RegisterRequest;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DuplicateEmailException;
import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.repository.UserAccountRepository;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.AuthService;
import com.baseProject.myBaseProject.service.JwtService;
import com.baseProject.myBaseProject.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final Clock clock;

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userAccountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        UserAccount account = UserAccount.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.PARTICIPANT)
                .enabled(true)
                .createdAt(clock.instant())
                .build();
        account = userAccountRepository.save(account);

        return issueTokens(new CustomUserDetails(account), account);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // create a userAccout only id has value to create a foreign key
        UserAccount accountRef = userAccountRepository.getReferenceById(userDetails.getId());

        return issueTokens(userDetails, accountRef);
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(refreshToken);

        CustomUserDetails userDetails = new CustomUserDetails(rotation.user());
        String accessToken = jwtService.generateAccessToken(userDetails);

        return toResult(accessToken, rotation.refreshToken(), userDetails);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional
    public int logoutAll(Long userId) {
        return refreshTokenService.revokeAllForUser(userId);
    }

    //create token and response
    private AuthResult issueTokens(CustomUserDetails userDetails, UserAccount accountRef) {
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = refreshTokenService.issue(accountRef);

        return toResult(accessToken, refreshToken, userDetails);
    }

    private AuthResult toResult(String accessToken, String refreshToken, CustomUserDetails userDetails) {
        AuthResponse body = new AuthResponse(
                accessToken,
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getRole()
        );
        return new AuthResult(body, refreshToken);
    }
}
