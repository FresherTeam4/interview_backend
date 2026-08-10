package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.auth.AuthResult;
import com.baseProject.myBaseProject.dto.auth.LoginRequest;
import com.baseProject.myBaseProject.dto.auth.RegisterRequest;

public interface AuthService {
    AuthResult register(RegisterRequest request);
    AuthResult login(LoginRequest request);
    AuthResult refresh(String refreshToken);
    void logout(String refreshToken);
    int logoutAll(Long userId);
}
